# OCR fixtures — real warranty cards

The scan pipeline is verified against the actual cards the app has to read, not invented
receipt text. This folder holds the harness that produces those captures.

## Files

| Path | What it is |
| --- | --- |
| `winocr.ps1` | Runs a real OCR engine over image files and prints one line of recognised text per line |
| `../../app/src/test/resources/ocr/win-scale1.txt` | Engine output for the three cards at native resolution |
| `../../app/src/test/resources/ocr/win-scale2.txt` | Same three cards upscaled 2x (what a full-resolution phone photo gives) |

The captures contain the engine's real mistakes — halved words (`nsi iS`), logo and signature
noise, and the boAt serial read as `41 IN2584769` at 1x. They are used verbatim: only
non-printable bytes were removed (glyph-recognition garbage from a handwritten signature).

## Regenerating

`winocr.ps1` uses the OCR engine built into Windows (`Windows.Media.Ocr`), so it needs no
installation. On other platforms use any engine and keep the same output shape (one
recognised line per line of text).

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/ocr/winocr.ps1 `
  -Scale 2 "path\to\card-front.png" "path\to\card-back.png" > app/src/test/resources/ocr/win-scale2.txt
```

Keep the `### FILE <name>` markers — the tests split the capture into cards on those lines.

## Checking the pipeline against a capture

```bash
./gradlew testDebugUnitTest --tests "com.warrantyvault.ocr.WinOcrCaptureTest" --rerun-tasks
```

The `parsed fields for every real capture` test prints what the parser extracted for every
card at every scale, so a regression is visible as a field going `null`:

```bash
grep -A 200 "OCR PIPELINE" app/build/test-results/testDebugUnitTest/TEST-com.warrantyvault.ocr.WinOcrCaptureTest.xml
```

## Why resolution matters

The same card gives two different qualities of text. At 1x the boAt card loses the serial
entirely; at 2x it reads `BT1411N2584769` (one character off the printed `BT141IN2584769`,
which the review screen exists to correct). The app feeds ML Kit the full-resolution camera
image for this reason — do not downscale before recognition.
