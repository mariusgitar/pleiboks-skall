# pleiboks-skall

Tynt Android-skall rundt [Pleiboks](https://github.com/mariusgitar/pleiboks). Det gjør en gammel Android-telefon (f.eks. Galaxy S7) om til en lydboks: barnet legger en NFC-brikke på telefonen, og lyden starter.

Pleiboks (nettsiden) gjør nesten alt: onboarding, foreldrepanel, avspilling, kobling av brikker. Skallet gjør bare det nettsiden ikke kan:

- **Leser NFC-brikker** (NTAG213/215 m.fl.) og sender ID-en til siden: `window.pleiboksBrikke("04A31F")`
- **Holder skjermen på** og viser siden i fullskjerm
- **Låser telefonen til appen** (startskjerm + skjermfesting)
- Lar lyd starte uten trykk på skjermen
- Viser «Pleiboksen trenger nett» hvis første oppstart skjer uten nett

## Installere

1. Åpne siste utgivelse under **Releases** på telefonen og last ned `pleiboks-boks.apk`.
2. Åpne filen. Første gang må du tillate installasjon fra nettleseren.
3. Åpne Pleiboks og følg oppsettet.

Nye versjoner installeres over den gamle (samme signeringsnøkkel), så brikker og lagrede episoder beholdes.

## Bro mot Pleiboks

Skallet legger inn objektet `PleiboksSkall` i siden:

| Metode | Hva den gjør |
|---|---|
| `versjon()` | `"1"` |
| `nfcPaa()` | `true` hvis NFC er slått på |
| `aapneNfcInnstillinger()` | Åpner NFC-innstillingene |
| `laas()` | Slår på kiosk: ber om å bli startskjerm, så skjermfesting |
| `avsluttKiosk()` | Slipper låsen og åpner valget av startskjerm |
| `provIgjen()` | Laster Pleiboks på nytt (brukes av «ingen nett»-siden) |

Pleiboks-siden av broen ligger i `src/skall.js` i pleiboks-repoet.

## Bygging

GitHub Actions (`.github/workflows/bygg.yml`) bygger APK-en ved hver push og legger den ut som en utgivelse. Bygg fra `main` blir «siste», andre grener blir forhåndsutgivelser.

Vil du at skallet skal åpne en annen adresse (f.eks. en testversjon), kjør workflowen manuelt under **Actions → Bygg APK → Run workflow** og fyll inn adressen.

Signeringsnøkkelen ligger som repo-hemmeligheter (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Uten dem bygges en debug-signert APK som ikke kan oppdateres over en tidligere versjon.
