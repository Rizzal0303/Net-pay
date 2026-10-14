<?php
// ============================================================
// FILE: config.php
// Konfigurasi Terpusat Netplus / BillingKu
// Satu-satunya file untuk mengatur Database, Midtrans, & Keamanan
// ============================================================

// 1. Kredensial Database MySQL
define('DB_HOST', 'localhost');
define('DB_NAME', 'billingku_db');
define('DB_USER', 'root');
define('DB_PASS', 'rizzal123'); // Cukup ubah di sini, otomatis berlaku di semua file API

// 2. Kredensial & Pengaturan Midtrans Payment Gateway
define('MIDTRANS_SERVER_KEY', 'SB-Mid-server-Oi0-stcLS91gmyUFBJx1MjkE');
define('MIDTRANS_IS_PRODUCTION', false); // Ganti true jika akun Midtrans sudah Live Production

// 3. API Key untuk Sinkronisasi Admin Aplikasi Kasir / Netplus
define('API_KEY', 'billingku_secret');

// 4. Nomor Kontak WhatsApp Cadangan (Fallback) Admin Netplus
define('DEFAULT_ADMIN_PHONE', '6281584210833');

// ------------------------------------------------------------
// Fungsi Bantu: Koneksi Database Tunggal (Singleton PDO)
// ------------------------------------------------------------
function getDbConnection() {
    static $pdo = null;
    if ($pdo === null) {
        $dsn = "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=utf8mb4";
        $options = [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ];
        try {
            $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
        } catch (PDOException $e) {
            http_response_code(500);
            header('Content-Type: application/json; charset=utf-8');
            echo json_encode([
                'success' => false,
                'status'  => 'error',
                'message' => 'Gagal terkoneksi ke database MySQL: ' . $e->getMessage()
            ]);
            exit;
        }
    }
    return $pdo;
}

// ------------------------------------------------------------
// Fungsi Bantu: Ambil Kontak Admin / Owner Dinamis dari Database
// ------------------------------------------------------------
function getAdminContactInfo($pdo = null) {
    if ($pdo === null) {
        $pdo = getDbConnection();
    }

    // 1. Cek dari tabel staff_members (prioritas role OWNER, lalu ADMIN)
    try {
        $stmt = $pdo->query("
            SELECT `name`, `phone`, `role` 
            FROM `staff_members` 
            WHERE UPPER(`role`) IN ('OWNER', 'ADMIN') 
              AND `isActive` = 1 
              AND `phone` IS NOT NULL 
              AND TRIM(`phone`) != '' 
            ORDER BY (CASE WHEN UPPER(`role`) = 'OWNER' THEN 1 ELSE 2 END), `id` ASC 
            LIMIT 1
        ");
        $staff = $stmt->fetch();
        if ($staff && !empty(trim($staff['phone']))) {
            return [
                'phone' => trim($staff['phone']),
                'name'  => $staff['name'] ?? 'Owner Netplus',
                'role'  => $staff['role'] ?? 'OWNER'
            ];
        }
    } catch (Exception $e) {
        // Lewati jika tabel staff_members belum dibuat
    }

    // 2. Cek dari tabel app_settings jika tersedia
    try {
        $stmt = $pdo->query("SELECT `settingValue` FROM `app_settings` WHERE `settingKey` = 'admin_whatsapp' LIMIT 1");
        $val = $stmt->fetchColumn();
        if ($val && !empty(trim($val))) {
            return [
                'phone' => trim($val),
                'name'  => 'Admin Netplus',
                'role'  => 'ADMIN'
            ];
        }
    } catch (Exception $e) {
        // Lewati
    }

    // 3. Fallback jika tidak ada record di database
    return [
        'phone' => DEFAULT_ADMIN_PHONE,
        'name'  => 'Customer Service Netplus',
        'role'  => 'ADMIN'
    ];
}

// ------------------------------------------------------------
// Fungsi Bantu: Periode Bulan Berjalan Otomatis (Bahasa Indonesia)
// ------------------------------------------------------------
function getCurrentPeriodIndo() {
    $months = [
        1 => 'Januari', 2 => 'Februari', 3 => 'Maret', 4 => 'April',
        5 => 'Mei', 6 => 'Juni', 7 => 'Juli', 8 => 'Agustus',
        9 => 'September', 10 => 'Oktober', 11 => 'November', 12 => 'Desember'
    ];
    $m = (int)date('n');
    $y = date('Y');
    return ($months[$m] ?? 'Januari') . ' ' . $y;
}

// ------------------------------------------------------------
// Fungsi Bantu: Generator Nomor Invoice Standar Faktur BillingKu
// Format: INV-YYYYMMDD-0001 (Berurut rapi per hari)
// ------------------------------------------------------------
function generateBillingKuInvoiceNumber($pdo = null) {
    if ($pdo === null) {
        $pdo = getDbConnection();
    }
    $today = date('Ymd');
    $prefix = "INV-{$today}-";

    try {
        $stmt = $pdo->prepare("
            SELECT `invoiceNumber` 
            FROM `transactions` 
            WHERE `invoiceNumber` LIKE :prefix 
            ORDER BY `invoiceNumber` DESC 
            LIMIT 1
        ");
        $stmt->execute([':prefix' => "{$prefix}%"]);
        $lastInv = $stmt->fetchColumn();

        if ($lastInv && preg_match('/-(\d+)$/', $lastInv, $matches)) {
            $nextSeq = (int)$matches[1] + 1;
        } else {
            $nextSeq = 1;
        }
    } catch (Exception $e) {
        $nextSeq = rand(1, 99);
    }

    return sprintf("%s%04d", $prefix, $nextSeq);
}

// ------------------------------------------------------------
// Fungsi Bantu: Menghitung Daftar Tunggakan Periode Pelanggan
// Mengembalikan daftar bulan yang belum dibayar, jumlah, & total akumulasi
// ------------------------------------------------------------
function calculateCustomerUnpaidPeriods($pdo, $customerId, $monthlyFee, $createdAt = 0) {
    $monthsIndo = [
        1 => 'Januari', 2 => 'Februari', 3 => 'Maret', 4 => 'April',
        5 => 'Mei', 6 => 'Juni', 7 => 'Juli', 8 => 'Agustus',
        9 => 'September', 10 => 'Oktober', 11 => 'November', 12 => 'Desember'
    ];

    // Ambil semua transaksi yang sudah lunas untuk pelanggan ini
    $paidPeriods = [];
    try {
        $stmt = $pdo->prepare("SELECT `periodMonth` FROM `transactions` WHERE `customerId` = ?");
        $stmt->execute([$customerId]);
        $rows = $stmt->fetchAll(PDO::FETCH_COLUMN);
        foreach ($rows as $r) {
            $paidPeriods[trim(strtolower($r))] = true;
        }
    } catch (Exception $e) {}

    // Tentukan berapa bulan ke belakang yang wajib dicek (default hingga 6 bulan terakhir s/d bulan berjalan)
    $maxCheckMonths = 6;
    if ($createdAt > 0) {
        $createdTimestamp = ($createdAt > 9999999999) ? (int)($createdAt / 1000) : (int)$createdAt;
        $monthsDiff = ((int)date('Y') * 12 + (int)date('n')) - ((int)date('Y', $createdTimestamp) * 12 + (int)date('n', $createdTimestamp)) + 1;
        if ($monthsDiff > 0 && $monthsDiff < $maxCheckMonths) {
            $maxCheckMonths = max(1, $monthsDiff);
        }
    }

    $unpaidList = [];
    for ($i = $maxCheckMonths - 1; $i >= 0; $i--) {
        $targetTime = strtotime("-$i month");
        $m = (int)date('n', $targetTime);
        $y = date('Y', $targetTime);
        $periodName = ($monthsIndo[$m] ?? '') . ' ' . $y;

        // Jika bulan ini belum ada di tabel transaksi lunas, masukkan ke daftar tunggakan
        if (!isset($paidPeriods[strtolower($periodName)])) {
            $unpaidList[] = $periodName;
        }
    }

    $unpaidCount = count($unpaidList);
    $totalDue = $unpaidCount * (int)$monthlyFee;
    $isPaid = ($unpaidCount === 0);

    $statusTagihan = 'LUNAS';
    if ($unpaidCount === 1) {
        $statusTagihan = 'BELUM LUNAS';
    } else if ($unpaidCount > 1) {
        $statusTagihan = "MENUNGGAK $unpaidCount BULAN";
    }

    return [
        'unpaidPeriods'     => $unpaidList,
        'unpaidMonthsCount' => $unpaidCount,
        'totalDue'          => $totalDue,
        'isPaid'            => $isPaid,
        'statusTagihan'     => $statusTagihan
    ];
}

// ------------------------------------------------------------
// Fungsi Bantu: Pencatatan Log Webhook Midtrans untuk Audit
// ------------------------------------------------------------
function logWebhookActivity($message, $payload = null) {
    $logFile = __DIR__ . '/midtrans_callback.log';
    $time = date('Y-m-d H:i:s');
    $entry = "[$time] " . $message;
    if ($payload !== null) {
        $entry .= " | " . (is_string($payload) ? $payload : json_encode($payload));
    }
    $entry .= PHP_EOL;
    @file_put_contents($logFile, $entry, FILE_APPEND);
}
?>
