@echo off
REM CRITICAL: Fix Git Cleanup - Remove Large Files from Tracking

cd /d "C:\Users\arivu\OneDrive\Desktop\OpenContinuity"

echo.
echo ============================================================================
echo STEP 1: Check what files need to be removed
echo ============================================================================
echo.
git ls-files windows/release/ | head -10
echo.

echo ============================================================================
echo STEP 2: REMOVE windows/release from Git tracking
echo ============================================================================
echo.
git rm -r --cached windows/release/
echo.

echo ============================================================================
echo STEP 3: Verify files removed from staging
echo ============================================================================
echo.
git status
echo.

echo ============================================================================
echo STEP 4: CREATE CLEANUP COMMIT
echo ============================================================================
echo.
git commit -m "Remove build artifacts from Git

Build executables should not be in version control:
- Removed windows/release/ directory (74MB + 168MB .exe files)
- Build artifacts bloat repository and cause push failures
- Users can download builds from GitHub Releases instead

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"

echo.
echo ============================================================================
echo STEP 5: View commit
echo ============================================================================
echo.
git log --oneline -n 3
echo.

echo ============================================================================
echo STEP 6: PUSH TO GITHUB
echo ============================================================================
echo.
git push origin main
echo.

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ============================================================================
    echo SUCCESS! Push completed without file size errors
    echo ============================================================================
    echo.
    echo Your files are still at: windows/release/
    echo Next: Upload to GitHub Releases
    echo.
) else (
    echo.
    echo ERROR during push. Check output above.
    echo.
)

pause
