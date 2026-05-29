# PowerShell Script - Git Cleanup
# Run this in PowerShell (not ISE) as Administrator

Set-Location "C:\Users\arivu\OneDrive\Desktop\OpenContinuity"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Step 1: Current Git Status" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
git status

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Step 2: Remove windows/release from Git" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
git rm -r --cached windows/release/

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Step 3: Verify Changes Staged" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
git status

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Step 4: Create Cleanup Commit" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

$commitMessage = @"
Remove build artifacts from Git

Build executables should not be in version control:
- Removed windows/release/ directory (74MB + 168MB .exe files)
- Build artifacts bloat repository and cause push failures
- Users can download builds from GitHub Releases instead

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
"@

git commit -m $commitMessage

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Step 5: Commit Created - View Log" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
git log --oneline -n 3

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Step 6: PUSHING TO GITHUB" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
git push origin main

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "✅ SUCCESS! Git push completed" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Your build files are still in:" -ForegroundColor Cyan
Write-Host "  C:\Users\arivu\OneDrive\Desktop\OpenContinuity\windows\release\" -ForegroundColor White
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "1. Go to: https://github.com/arivuchezhiyan/OpenContinuity/releases" -ForegroundColor White
Write-Host "2. Create a new release (tag: v1.0.0)" -ForegroundColor White
Write-Host "3. Upload your .exe files from windows/release/" -ForegroundColor White
Write-Host ""
Read-Host "Press Enter to exit"
