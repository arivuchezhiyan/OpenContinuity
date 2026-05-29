@echo off
REM ============================================================================
REM FINAL SOLUTION: Remove large files from Git and push everything
REM ============================================================================

setlocal enabledelayedexpansion

cd /d "C:\Users\arivu\OneDrive\Desktop\OpenContinuity"

echo.
echo ============================================================================
echo OPENCONTINUITY - FINAL GIT CLEANUP & PUSH
echo ============================================================================
echo.

REM Step 1: Show current status
echo [STEP 1] Current Git Status
echo ============================================================================
git status --short
echo.

REM Step 2: Remove large files from Git
echo [STEP 2] Removing large files from Git tracking...
echo ============================================================================
echo Executing: git rm -r --cached windows/release/
echo.
git rm -r --cached windows/release/
if !errorlevel! neq 0 (
    echo ERROR: Failed to remove files from git
    goto ERROR
)
echo ✓ Files removed from Git tracking (files still on disk)
echo.

REM Step 3: Check status
echo [STEP 3] Verifying changes staged for commit
echo ============================================================================
git status --short | findstr "D windows/release" | head -5
echo ... (showing first 5 deletions)
echo.

REM Step 4: Create commit
echo [STEP 4] Creating cleanup commit...
echo ============================================================================
git commit -m "Remove build artifacts from Git

Build executables should not be in version control:
- Removed windows/release/ directory (74MB + 168MB .exe files)
- Build artifacts bloat repository and cause push failures
- Users can download builds from GitHub Releases instead

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"

if !errorlevel! neq 0 (
    echo ERROR: Failed to create commit
    goto ERROR
)
echo ✓ Commit created successfully
echo.

REM Step 5: Show commit
echo [STEP 5] Verifying commit...
echo ============================================================================
git log --oneline -n 2
echo.

REM Step 6: Push to GitHub
echo [STEP 6] PUSHING TO GITHUB...
echo ============================================================================
echo This may take 30-60 seconds...
echo.

git push origin main
if !errorlevel! neq 0 (
    echo ERROR: Failed to push to GitHub
    echo Check output above for details
    goto ERROR
)
echo.

REM Step 7: Final verification
echo [STEP 7] VERIFYING SUCCESS...
echo ============================================================================
git log --oneline -n 1
echo.

REM Success message
echo.
echo ============================================================================
echo ✅ SUCCESS! Git push completed!
echo ============================================================================
echo.
echo Your large .exe files are still locally at:
echo   C:\Users\arivu\OneDrive\Desktop\OpenContinuity\windows\release\
echo.
echo Next steps:
echo 1. Go to: https://github.com/arivuchezhiyan/OpenContinuity
echo 2. Click: Releases
echo 3. Create: New Release (v1.0.0)
echo 4. Upload: Your .exe files
echo 5. Publish: Release
echo.
echo Users will now download from Releases tab!
echo.
pause
exit /b 0

:ERROR
echo.
echo ============================================================================
echo ❌ ERROR: Something went wrong
echo ============================================================================
echo.
echo Try running these commands manually in Git Bash:
echo.
echo 1. cd C:\Users\arivu\OneDrive\Desktop\OpenContinuity
echo 2. git rm -r --cached windows/release/
echo 3. git commit -m "Remove build artifacts from Git"
echo 4. git push origin main
echo.
echo Contact support if issues persist.
echo.
pause
exit /b 1
