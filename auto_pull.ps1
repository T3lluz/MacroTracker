# auto_pull.ps1
# Polling script to automatically pull changes from the remote repository.

$Interval = 60 # Seconds
$Remote = "origin"

Write-Host "Starting Auto-Pull script. Polling every $Interval seconds..." -ForegroundColor Cyan

while ($true) {
    try {
        # Fetch the latest state from remote
        git fetch $Remote | Out-Null

        # Get the current branch name
        $BRANCH = git rev-parse --abbrev-ref HEAD

        # Get the local and remote commit hashes
        $LOCAL = git rev-parse @
        $UPSTREAM = git rev-parse "@{u}"

        if ($LOCAL -eq $UPSTREAM) {
            # Up to date, do nothing
        } elseif ($LOCAL -eq (git merge-base @ "@{u}")) {
            # Local is behind upstream - Pull
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] New commits detected on $BRANCH. Pulling..." -ForegroundColor Green
            git pull
        } elseif ($UPSTREAM -eq (git merge-base @ "@{u}")) {
            # Local is ahead of upstream - Don't pull (would cause merge or error)
            # Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Local branch is ahead of $Remote/$BRANCH. Skipping pull." -ForegroundColor Yellow
        } else {
            # Diverged - Manual intervention usually required
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Local and remote have diverged. Manual merge required." -ForegroundColor Red
        }
    } catch {
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Error checking for updates: $_" -ForegroundColor Red
    }

    Start-Sleep -Seconds $Interval
}
