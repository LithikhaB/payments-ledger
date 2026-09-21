# Demo of the ledger API. Start the app first (http://localhost:8080), then run:
#   powershell -ExecutionPolicy Bypass -File .\scripts\demo.ps1
$base = "http://localhost:8080/api/v1"

function Call($method, $path, $body, $key) {
    $headers = @{}
    if ($key) { $headers["Idempotency-Key"] = $key }
    $req = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
    if ($body) {
        $req.Body = ($body | ConvertTo-Json)
        $req.ContentType = "application/json"
    }
    try {
        $r = Invoke-WebRequest @req
        return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = ($r.Content | ConvertFrom-Json) }
    } catch {
        return [pscustomobject]@{ Status = [int]$_.Exception.Response.StatusCode; Body = $_.ErrorDetails.Message }
    }
}

$alice = (Call Post "/accounts" @{ ownerName = "alice"; currency = "INR" }).Body
$bob   = (Call Post "/accounts" @{ ownerName = "bob";   currency = "INR" }).Body

"Deposit 1000.00 INR (100000 paise) into alice -> " + (Call Post "/accounts/$($alice.id)/deposits" @{ amount = 100000 } "demo-dep-$(Get-Random)").Status

$key = "demo-transfer-$(Get-Random)"
$transfer = @{ fromAccountId = $alice.id; toAccountId = $bob.id; amount = 25000; currency = "INR" }
"First call        -> " + (Call Post "/transfers" $transfer $key).Status + "  (expect 201)"
"Replay same key   -> " + (Call Post "/transfers" $transfer $key).Status + "  (expect 200, nothing moves)"
$transfer.amount = 99999
"Same key, new body-> " + (Call Post "/transfers" $transfer $key).Status + "  (expect 422)"

"alice balance: " + (Call Get "/accounts/$($alice.id)").Body.balance + "  (expect 75000)"
"bob balance:   " + (Call Get "/accounts/$($bob.id)").Body.balance + "  (expect 25000)"
