<?php
// ============================================================
// FILE: midtrans_notification.php
// Lokasi: https://billing.gonetplus.web.id/midtrans_notification.php
// Fungsi: Webhook otomatis dari Midtrans saat pembayaran selesai
// Masukkan URL ini di Dashboard Midtrans > Settings > Configuration > Payment Notification URL
// ============================================================
header('Content-Type: application/json; charset=utf-8');

// ------------------------------------------------------------
// 1. Memuat Konfigurasi Terpusat
// ------------------------------------------------------------
require_once __DIR__ . '/config.php';

$rawInput = file_get_contents('php://input');
$notification = json_decode($rawInput, true);

// Catat setiap callback masuk ke file log untuk audit
logWebhookActivity("Notification Callback Received", $rawInput);

if (!$notification) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'Invalid payload']);
    exit;
}

$orderId           = $notification['order_id'] ?? '';
$statusCode        = $notification['status_code'] ?? '';
$grossAmount       = $notification['gross_amount'] ?? '';
$signatureKey      = $notification['signature_key'] ?? '';
$transactionStatus = $notification['transaction_status'] ?? '';
$fraudStatus       = $notification['fraud_status'] ?? '';
$paymentType       = $notification['payment_type'] ?? 'midtrans';

$serverKey = MIDTRANS_SERVER_KEY;

// ------------------------------------------------------------
// 2. Verifikasi Keamanan Signature SHA512
// ------------------------------------------------------------
$calculatedSignature = hash('sha512', $orderId . $statusCode . $grossAmount . $serverKey);
if ($calculatedSignature !== $signatureKey) {
    // Jika masih memakai server key sandbox contoh di development, catat warning
    if (strpos($serverKey, 'XXXXXX') !== false) {
        logWebhookActivity("Warning: Server key placeholder detected, skipping strict signature check for testing");
    } else {
        logWebhookActivity("Signature Mismatch: expected $calculatedSignature, received $signatureKey");
        http_response_code(403);
        echo json_encode(['status' => 'error', 'message' => 'Invalid signature']);
        exit;
    }
}

// ------------------------------------------------------------
// 3. Cek Status Pembayaran (Settlement / Capture)
// ------------------------------------------------------------
$isSuccess = false;
if ($transactionStatus === 'capture') {
    if ($fraudStatus === 'accept') {
        $isSuccess = true;
    }
} else if ($transactionStatus === 'settlement') {
    $isSuccess = true;
}

if ($isSuccess) {
    // Ekstrak Customer ID dari Order ID (format baru: INV-YYYYMMDD-{customerId}-{rand} atau format lama: INV-{customerId}-{timestamp})
    $parts = explode('-', $orderId);
    $customerId = 0;
    if (count($parts) >= 4) {
        $customerId = (int)$parts[2];
    } else if (count($parts) >= 2) {
        $customerId = (int)$parts[1];
    }

    if ($customerId > 0) {
        try {
            $pdo = getDbConnection();

            // Ambil data pelanggan
            $stmtCust = $pdo->prepare("SELECT * FROM `customers` WHERE `id` = ? LIMIT 1");
            $stmtCust->execute([$customerId]);
            $customer = $stmtCust->fetch();

            if ($customer) {
                // Cari seluruh periode yang belum dibayar pelanggan
                $unpaidAnalysis = calculateCustomerUnpaidPeriods($pdo, $customer['id'], $customer['monthlyFee'], $customer['createdAt'] ?? 0);
                $unpaidPeriods = $unpaidAnalysis['unpaidPeriods'];

                if (empty($unpaidPeriods)) {
                    $unpaidPeriods = [getCurrentPeriodIndo()];
                }

                $paidAt      = time() * 1000;
                $method      = strtoupper($paymentType);
                $cashierName = 'Midtrans'; // Nama kasir ringkas sesuai standar BillingKu
                $monthlyFee  = (int)$customer['monthlyFee'];

                // Masukkan / update ke tabel `transactions` untuk setiap periode tunggakan yang diselesaikan
                $stmtInsert = $pdo->prepare("
                    INSERT INTO `transactions` (
                        `id`, `invoiceNumber`, `customerId`, `customerName`, `customerCode`, 
                        `packageName`, `monthlyFee`, `adminFee`, `discount`, `totalAmount`, 
                        `periodMonth`, `paymentMethod`, `cashierName`, `cashierRole`, `notes`, `paidAt`
                    ) VALUES (
                        :id, :inv, :cid, :cname, :ccode, 
                        :pkg, :fee, 0, 0, :total, 
                        :period, :method, :cashier, 'GATEWAY', :notes, :paidAt
                    ) ON DUPLICATE KEY UPDATE 
                        `totalAmount` = VALUES(`totalAmount`), 
                        `paymentMethod` = VALUES(`paymentMethod`),
                        `cashierName` = VALUES(`cashierName`),
                        `paidAt` = VALUES(`paidAt`);
                ");

                foreach ($unpaidPeriods as $idx => $periodMonth) {
                    $invoiceNumber = generateBillingKuInvoiceNumber($pdo);
                    $notes = "Pembayaran online Midtrans (Order: $orderId - Periode: $periodMonth)";
                    $newTransId = (int)(time() . rand(100, 999)) + $idx;

                    $stmtInsert->execute([
                        ':id'      => $newTransId,
                        ':inv'     => $invoiceNumber,
                        ':cid'     => $customer['id'],
                        ':cname'   => $customer['name'],
                        ':ccode'   => $customer['customerCode'],
                        ':pkg'     => $customer['packageName'] ?: 'Internet WiFi',
                        ':fee'     => $monthlyFee,
                        ':total'   => $monthlyFee,
                        ':period'  => $periodMonth,
                        ':method'  => $method,
                        ':cashier' => $cashierName,
                        ':notes'   => $notes,
                        ':paidAt'  => $paidAt
                    ]);

                    logWebhookActivity("Success: Recorded transaction for Customer {$customer['name']} ($customerId), Period $periodMonth, Invoice $invoiceNumber, Amount $monthlyFee");
                }
            } else {
                logWebhookActivity("Warning: Customer ID $customerId not found in database for Order $orderId");
            }
        } catch (Exception $e) {
            logWebhookActivity("Database Error: " . $e->getMessage());
        }
    }
} else {
    logWebhookActivity("Status not settlement/capture ($transactionStatus, fraud: $fraudStatus)");
}

echo json_encode(['status' => 'success', 'message' => 'Notification processed']);
?>
