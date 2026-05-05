var fs = require('fs');
var apk = 'Z:/ironlog/android/app/build/outputs/apk/release/app-release.apk';
// Current APK mtime in ms — wait for anything newer than this
var baseline = fs.statSync(apk).mtimeMs;
console.log('Baseline APK mtime: ' + new Date(baseline));
var tries = 0;
function check() {
  tries++;
  try {
    var s = fs.statSync(apk);
    if (s.mtimeMs > baseline) {
      console.log('APK UPDATED: ' + s.mtime + ' size=' + s.size);
    } else if (tries < 40) {
      setTimeout(check, 15000);
    } else {
      console.log('TIMEOUT waiting for new APK');
    }
  } catch(e) { console.log('err: ' + e.message); }
}
setTimeout(check, 15000);
