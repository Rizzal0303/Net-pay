<?php
// ============================================================
// FILE: api_check_bill.php
// Lokasi Web Server: https://billing.gonetplus.web.id/api_check_bill.php
// Fungsi: 
// 1. Cek tagihan & riwayat transaksi pelanggan WiFi
// 2. Mengambil kontak dinamis Admin/Owner secara otomatis dari database
// 3. Tes Ping & status koneksi server
// ============================================================
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-API-Key');

// Tangani request preflight OPTIONS
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// ------------------------------------------------------------
// 1. Memuat Konfigurasi Terpusat & Koneksi Database MySQL
// ------------------------------------------------------------
require_once __DIR__ . '/config.php';
$pdo = getDbConnection();

// Ambil info kontak admin dinamis
$adminInfo = getAdminContactInfo($pdo);
$adminPhone = $adminInfo['phone'];

// ------------------------------------------------------------
// Endpoint: Tes Ping (?ping=1)
// ------------------------------------------------------------
if (isset($_GET['ping'])) {
    echo json_encode([
        'success'    => true,
        'message'    => 'Server aktif & database terhubung',
        'adminPhone' => $adminPhone,
        'timestamp'  => time()
    ]);
    exit;
}

// ------------------------------------------------------------
// Endpoint: Ambil Kontak Admin (?action=get_admin_contact)
// Dipanggil oleh aplikasi Netplus saat pertama dibuka
// ------------------------------------------------------------
$action = $_GET['action'] ?? '';
if ($action === 'get_admin_contact' || isset($_GET['get_admin_contact'])) {
    echo json_encode([
        'success'    => true,
        'status'     => 'success',
        'phone'      => $adminPhone,
        'adminPhone' => $adminPhone,
        'data'       => [
            'phone'       => $adminPhone,
            'admin_phone' => $adminPhone,
            'name'        => $adminInfo['name'],
            'role'        => $adminInfo['role']
        ],
        'message'    => 'Kontak admin/owner berhasil diambil'
    ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
    exit;
}

// ------------------------------------------------------------
// 2. Ambil Parameter ID Pelanggan (contoh: 1791158991543384)
// ------------------------------------------------------------
$idInput = isset($_GET['id']) ? trim($_GET['id']) : (isset($_GET['customerId']) ? trim($_GET['customerId']) : '');

if (empty($idInput)) {
    http_response_code(400);
    echo json_encode([
        'success'    => false,
        'adminPhone' => $adminPhone,
        'message'    => 'Parameter id pelanggan wajib diisi! Contoh: api_check_bill.php?id=1791158991543384'
    ]);
    exit;
}

// ------------------------------------------------------------
// 3. Query Data Pelanggan dari Tabel `customers`
// ------------------------------------------------------------
$stmt = $pdo->prepare("SELECT * FROM `customers` WHERE `id` = ? OR UPPER(`customerCode`) = UPPER(?) LIMIT 1");
$stmt->execute([$idInput, $idInput]);
$customer = $stmt->fetch();

if (!$customer) {
    http_response_code(404);
    echo json_encode([
        'success'    => false,
        'adminPhone' => $adminPhone,
        'message'    => "Pelanggan dengan ID / Kode '$idInput' tidak ditemukan dalam database."
    ]);
    exit;
}

// Konversi data numerik pelanggan
$customer['id'] = (int)$customer['id'];
$customer['monthlyFee'] = (int)$customer['monthlyFee'];
$customer['dueDay'] = (int)($customer['dueDay'] ?? 10);

// ------------------------------------------------------------
// 4. Query Riwayat Transaksi dari Tabel `transactions`
// ------------------------------------------------------------
$stmtTrans = $pdo->prepare("SELECT * FROM `transactions` WHERE `customerId` = ? ORDER BY `paidAt` DESC");
$stmtTrans->execute([$customer['id']]);
$history = $stmtTrans->fetchAll();

foreach ($history as &$t) {
    $t['id'] = (int)$t['id'];
    $t['customerId'] = (int)$t['customerId'];
    $t['monthlyFee'] = (int)$t['monthlyFee'];
    $t['adminFee'] = (int)($t['adminFee'] ?? 0);
    $t['discount'] = (int)($t['discount'] ?? 0);
    $t['totalAmount'] = (int)$t['totalAmount'];
    $t['paidAt'] = (int)$t['paidAt'];
}
unset($t);

// ------------------------------------------------------------
// 5. Hitung Tunggakan & Periode Belum Dibayar Secara Akumulatif
// ------------------------------------------------------------
$currentPeriod = getCurrentPeriodIndo();
$unpaidAnalysis = calculateCustomerUnpaidPeriods($pdo, $customer['id'], $customer['monthlyFee'], $customer['createdAt'] ?? 0);

$isPaid            = $unpaidAnalysis['isPaid'];
$totalDue          = $unpaidAnalysis['totalDue'];
$unpaidPeriods     = $unpaidAnalysis['unpaidPeriods'];
$unpaidMonthsCount = $unpaidAnalysis['unpaidMonthsCount'];
$statusTagihan     = $unpaidAnalysis['statusTagihan'];

$paidTransaction = null;
foreach ($history as $item) {
    if (strcasecmp($item['periodMonth'], $currentPeriod) === 0) {
        $paidTransaction = $item;
        break;
    }
}
if (!$paidTransaction && !empty($history)) {
    $paidTransaction = $history[0];
}

$dueDayStr = str_pad($customer['dueDay'], 2, '0', STR_PAD_LEFT);

// ------------------------------------------------------------
// 6. Output Respon JSON Lengkap
// ------------------------------------------------------------
echo json_encode([
    'success'           => true,
    'message'           => 'Data tagihan berhasil diambil',
    'phone'             => $adminPhone,
    'adminPhone'        => $adminPhone,
    'companyPhone'      => $adminPhone,
    'adminName'         => $adminInfo['name'],
    'customer'          => $customer,
    'currentPeriod'     => $currentPeriod,
    'isPaid'            => $isPaid,
    'paidTransaction'   => $paidTransaction,
    'dueDate'           => "Tanggal $dueDayStr $currentPeriod",
    'packageFee'        => $customer['monthlyFee'],
    'adminFee'          => 0,
    'discount'          => 0,
    'totalDue'          => $totalDue,
    'unpaidMonthsCount' => $unpaidMonthsCount,
    'unpaidPeriods'     => $unpaidPeriods,
    'statusTagihan'     => $statusTagihan,
    'history'           => $history
], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
?>
