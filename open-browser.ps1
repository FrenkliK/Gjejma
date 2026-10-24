$base = 'http://localhost:3000'
$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -Uri "$base/api/health" -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            Start-Process $base
            exit 0
        }
    } catch {
        Start-Sleep -Milliseconds 500
    }
}
Start-Process $base
