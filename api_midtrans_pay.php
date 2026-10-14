<?php
// ============================================================
// FILE: api_midtrans_pay.php
// Lokasi: https://billing.gonetplus.web.id/api_midtrans_pay.php
// Fungsi: Membuat transaksi Snap Midtrans untuk pembayaran pelanggan
// ============================================================
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-API-Key');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// ------------------------------------------------------------
// 1. Memuat Konfigurasi Terpusat & Koneksi Database MySQL
// ------------------------------------------------------------
require_once __DIR__ . '/config.php';
$pdo = getDbConnection();

// ------------------------------------------------------------
// 2. Ambil Parameter ID Pelanggan
// ------------------------------------------------------------
$idInput = isset($_GET['id']) ? trim($_GET['id']) : (isset($_GET['customerId']) ? trim($_GET['customerId']) : '');

if (empty($idInput)) {
    http_response_code(400);
    echo json_encode(['success' => false, 'message' => 'Parameter id pelanggan wajib diisi']);
    exit;
}

// ------------------------------------------------------------
// 3. Cari Data Pelanggan di Database
// ------------------------------------------------------------
$stmt = $pdo->prepare("SELECT * FROM `customers` WHERE `id` = ? OR UPPER(`customerCode`) = UPPER(?) LIMIT 1");
$stmt->execute([$idInput, $idInput]);
$customer = $stmt->fetch();

if (!$customer) {
    http_response_code(404);
    echo json_encode(['success' => false, 'message' => 'Pelanggan tidak ditemukan di database']);
    exit;
}

$monthlyFee = (int)$customer['monthlyFee'];
if ($monthlyFee <= 0) {
    $monthlyFee = 110000;
}

$currentPeriod = getCurrentPeriodIndo();
$todayDate = date('Ymd');
$randomSuffix = rand(100, 999);
$orderId = "INV-{$todayDate}-{$customer['id']}-{$randomSuffix}";

// Hitung seluruh periode yang belum dibayar (tunggakan akumulasi)
$unpaidAnalysis = calculateCustomerUnpaidPeriods($pdo, $customer['id'], $monthlyFee, $customer['createdAt'] ?? 0);
$unpaidPeriods = $unpaidAnalysis['unpaidPeriods'];
$totalAmount = $unpaidAnalysis['totalDue'];

if ($totalAmount <= 0 || empty($unpaidPeriods)) {
    $totalAmount = $monthlyFee;
    $unpaidPeriods = [$currentPeriod];
}

// Buat rincian item untuk setiap bulan yang belum dibayar di Midtrans
$itemDetails = [];
foreach ($unpaidPeriods as $idx => $period) {
    $itemDetails[] = [
        'id'       => 'WIFI-' . $customer['id'] . '-' . ($idx + 1),
        'price'    => $monthlyFee,
        'quantity' => 1,
        'name'     => 'Tagihan ' . $period . ' (' . ($customer['packageName'] ?: 'Internet') . ')'
    ];
}

// ------------------------------------------------------------
// 4. Buat Payload Snap Midtrans
// ------------------------------------------------------------
$snapPayload = [
    'transaction_details' => [
        'order_id'     => $orderId,
        'gross_amount' => $totalAmount
    ],
    'customer_details' => [
        'first_name'      => $customer['name'],
        'phone'           => !empty($customer['phone']) ? $customer['phone'] : '081234567890',
        'billing_address' => [
            'address' => !empty($customer['address']) ? $customer['address'] : 'Alamat Pelanggan'
        ]
    ],
    'item_details' => $itemDetails,
    'gopay' => [
        'enable_callback' => true,
        'callback_url'    => 'https://billing.gonetplus.web.id/api_check_bill.php?midtrans_done=1'
    ],
    'shopeepay' => [
        'callback_url'    => 'https://billing.gonetplus.web.id/api_check_bill.php?midtrans_done=1'
    ],
    'callbacks' => [
        'finish' => 'https://billing.gonetplus.web.id/api_check_bill.php?midtrans_done=1'
    ]
];

// ------------------------------------------------------------
// 5. Request Token ke Midtrans Snap API via cURL
// ------------------------------------------------------------
$apiUrl = MIDTRANS_IS_PRODUCTION 
    ? 'https://app.midtrans.com/snap/v1/transactions' 
    : 'https://app.sandbox.midtrans.com/snap/v1/transactions';

$serverKey = MIDTRANS_SERVER_KEY;

$ch = curl_init();
curl_setopt($ch, CURLOPT_URL, $apiUrl);
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_POST, true);
curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($snapPayload));
curl_setopt($ch, CURLOPT_HTTPHEADER, [
    'Content-Type: application/json',
    'Accept: application/json',
    'Authorization: Basic ' . base64_encode($serverKey . ':')
]);

$response = curl_exec($ch);
$httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);

$result = json_decode($response, true);

if ($httpCode === 201 || (isset($result['token']) && !empty($result['token']))) {
    $redirectUrl = $result['redirect_url'] ?? (
        MIDTRANS_IS_PRODUCTION
            ? "https://app.midtrans.com/snap/v4/redirection/{$result['token']}"
            : "https://app.sandbox.midtrans.com/snap/v4/redirection/{$result['token']}"
    );

    echo json_encode([
        'success'      => true,
        'token'        => $result['token'],
        'redirect_url' => $redirectUrl
    ]);
} else {
    // Demo simulator fallback jika key sandbox contoh belum diaktifkan
    if (strpos($serverKey, 'Oi0-stcLS91gmyUFBJx1MjkE') !== false) {
        echo json_encode([
            'success'      => true,
            'token'        => 'sample_token',
            'redirect_url' => 'https://simulator.sandbox.midtrans.com/qris/index',
            'message'      => 'Catatan: Ganti MIDTRANS_SERVER_KEY di config.php dengan key dashboard Anda'
        ]);
    } else {
        http_response_code($httpCode ?: 500);
        echo json_encode([
            'success' => false,
            'message' => $result['error_messages'][0] ?? 'Gagal membuat sesi pembayaran Midtrans'
        ]);
    }
}
?>
