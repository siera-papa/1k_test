# 1K Test 0.1.18

U terminalu iz ovog foldera, sa povezanim telefonom:

```bash
chmod +x gradlew
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Instalacija preko prethodne verzije zadržava postojeće podatke. Ne deinstaliraj aplikaciju.

Android build nije pokretan ovde. Provereni su sintaksa i XML; ponašanje na telefonu proverava se lokalno.

Automatski JSON backup: Downloads/1K Test, poslednja potpuna kopija za svaki dan. Status: ⋮ → Automatski backup.
