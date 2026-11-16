# ASA Black Box și ASA SysId: ghid complet

Două biblioteci gratuite pentru roboții FTC, făcute de echipa **ASA (FTC 28284, Moldova)**.

- **ASA Black Box** este „cutia neagră” a robotului. Înregistrează tot ce se întâmplă cu robotul, observă singură
  defectele și compară fiecare antrenament cu cele de dinainte.
- **ASA SysId** măsoară un mecanism (lift, braț, flywheel, șasiu) și dă constantele de feedforward (kS, kG, kV, kA).
  Apoi arată în cifre că mecanismul merge mai precis cu ele.

Ambele rulează pe Control Hub. Le controlați cu gamepad-ul de pe Driver Hub, ca pe un OpMode obișnuit. Nu trebuie
instalat nimic în afară de fișierele bibliotecii.

---

## 1. Instalare (2 minute)

### Android Studio

1. Deschideți [ultimul release](https://github.com/stanchevicid-hash/asa-blackbox/releases/latest) și descărcați
   `asa-blackbox-dropin.zip`. Pentru SysId, descărcați `asa-sysid-dropin.zip` de pe
   [pagina SysId](https://github.com/stanchevicid-hash/asa-sysid/releases/latest).
2. Dezarhivați **în rădăcina proiectului FtcRobotController** (acolo unde sunt folderele `TeamCode` și
   `FtcRobotController`). Fișierele ajung singure în `TeamCode/src/main/java/org/asa/tools/`.
3. Apăsați ▶ Run și încărcați codul pe robot, ca de obicei.

Aveți nevoie de ambele biblioteci? Dezarhivați ambele arhive. Fișierele comune sunt identice, pot fi suprascrise.

### OnBot Java (din browser, fără Android Studio)

**Cel mai simplu:** descărcați din release `asa-blackbox-onbot.zip`, dezarhivați și încărcați toate cele 14 fișiere în
folderul obișnuit `teamcode` din OnBot Java, fără subfoldere. Apăsați Build. Dacă prima compilare nu reușește
(„bad source file”), apăsați Build încă o dată.

**Sau cu foldere:**

1. Conectați-vă la Wi-Fi-ul robotului și deschideți `http://192.168.43.1:8080` → **OnBot Java**.
2. Creați folderele `org/asa/tools/core`, `org/asa/tools/ftc`, `org/asa/tools/blackbox` (și `org/asa/tools/sysid`
   pentru SysId).
3. Încărcați în fiecare folder fișierele `.java` din folderul cu același nume din `src/` al repository-ului.
   Apăsați Build.

Merge cu FTC SDK de la 9.x la 12.x. Biblioteca a fost compilată cu SDK-urile oficiale 12.0.0 și 9.2.0.

---

## 2. Programe gata făcute pe Driver Hub (fără cod)

După instalare, în lista de OpMode-uri de pe Driver Hub, grupul **ASA**, apar două programe.

### ASA Health Check: verificarea robotului înainte de meci

| Când | Buton | Ce face |
|---|---|---|
| INIT | dpad ←/→ | ce baterie e pusă (A…H): starea fiecărei baterii este ținută minte |
| după START | dpad ↑/↓ | alegeți motorul |
| | **țineți A / B** | motorul se învârte cu puterea +0,3 / −0,3, **doar cât timp țineți butonul** |
| | **X** | terminați și vedeți raportul |

Pe ecran apar:

- fiecare hub: dacă răspunde, temperatura, tensiunea, **versiunea de firmware**;
- pentru motorul ales: dacă encoderul numără și în ce direcție;
- istoricul bateriilor: rezistența internă și care baterie a devenit mai slabă decât era.

**Înainte de verificare ridicați robotul sau faceți loc: motorul ales se va învârti.**

### ASA Center of Gravity: centrul de greutate

Motoarele nu se mișcă. E nevoie doar de IMU-ul hub-ului (în configurație trebuie să se numească `imu`).

1. INIT: cu dpad ↑/↓ setați **ampatamentul** (distanța față-spate dintre roți), cu ←/→ **ecartamentul**
   (distanța stânga-dreapta), în centimetri, de la punctul de contact al roții cu podeaua la celălalt. START.
2. Cinci pași. La fiecare puneți robotul cum scrie pe ecran, așteptați să nu se mai clatine și apăsați **A**:
   - drept pe podea;
   - pe roțile din spate: ridicați partea din față până când robotul stă singur în echilibru;
   - pe roțile din față;
   - pe roțile din dreapta;
   - pe roțile din stânga.
3. Rezultatul: înălțimea centrului de greutate, poziția lui față-spate și stânga-dreapta. Mai aflați cu ce
   accelerație poate robotul să frâneze, să accelereze și să vireze fără să se răstoarne, și pe ce pantă maximă
   poate sta.

**B** = un pas înapoi, **Y** = de la început. Măsurați încă o dată cu liftul ridicat: veți vedea cât de mult ridică
liftul centrul de greutate. Rezultatul se salvează în `/sdcard/FIRST/asa/`.

---

## 3. Black Box în propriul TeleOp sau Autonomous

Ajung câteva rânduri în OpMode:

```java
BlackBox bb = BlackBox.create("teleop");                 // numele sesiunii
FtcBlackBox.attachAll(bb, hardwareMap);                   // TOT din configurație + toate regulile
FtcBlackBox.attachGamepads(bb, gamepad1, gamepad2);       // stick-urile piloților pe aceeași axă de timp
bb.battery("A");                                          // ce baterie e pusă (opțional)
AsaWeb.start(); AsaWeb.publish(bb);                       // pagina web (opțional)

waitForStart();
bb.start();
try {
    while (opModeIsActive()) {
        // ... codul vostru ...
        bb.update();                                      // o dată pe ciclu
        FtcBlackBox.show(telemetry, bb);                  // avertismente pe Driver Hub
        telemetry.update();
    }
} catch (RuntimeException e) { bb.crash(e); throw e; }    // dacă programul cade, salvează cauza
finally { bb.stop(); }                                    // raport și comparație cu antrenamentele trecute
```

Exemplu complet: `src/org/asa/tools/examples/ExampleTeleOpBlackBox.java`.

### Ce se înregistrează singur (`attachAll`)

| Dispozitiv | Ce se scrie |
|---|---|
| fiecare motor | putere, poziție, viteză, curent |
| fiecare servo | poziție |
| IMU | direcția (yaw) |
| senzori de distanță | distanța în mm (în afara domeniului: gol) |
| intrări analogice | tensiune |
| butoane (touch) | apăsat / nu |
| hub-uri | dacă răspunde, temperatura |
| baterie | tensiune, curent total, rezistență internă |
| gamepad-uri (`attachGamepads`) | stick-uri, triggere, dacă e conectat |
| ciclu | durata ciclului în ms |

Puteți înregistra și valori și evenimente proprii:

```java
bb.channel("lift").signal("target", () -> target);       // o valoare proprie
bb.event("INTAKE_JAM", "slot 2");                         // un eveniment propriu pe axa de timp
```

### Ce observă Black Box singur

Tot ce e mai jos se pornește cu un singur rând, `attachAll`. Fiecare avertisment apare pe Driver Hub, în jurnalul
de evenimente și în raport.

| Avertisment | Ce s-a întâmplat | Ce faceți |
|---|---|---|
| `motor-stall` | curent mare, motorul nu se învârte deși primește putere (0,5 s; motor fără encoder: 1 s) | ceva s-a blocat: un opritor, un element înțepenit, o curea prea strânsă |
| `motor-encoder-lost` | **CRITICAL.** motorul se învârte liber, dar encoderul arată 0 | verificați cablul encoderului |
| `motor-hot` | curentul mediu din ultimele ~2 minute peste 5 A | motorul se încălzește și pierde din forță: ușurați mecanismul, scădeți puterea de menținere |
| `roată-drags` | o roată a șasiului, la aceeași putere și viteză, ia de 2 ori mai mult curent decât celelalte, 2 s la rând | frecare: ax îndoit, roata freacă, lanț prea strâns, reductor pe moarte |
| `hub-of-…-reset` | **CRITICAL.** toate encoderele unui hub s-au resetat deodată | hub-ul a repornit: electricitate statică, contact slab la alimentare. Cauză frecventă pentru „robotul a înghețat o jumătate de secundă” |
| `hub-not-responding` | **CRITICAL.** hub-ul nu mai răspunde | cablul RS-485 / USB, alimentarea hub-ului |
| `gamepadN-disconnected` | **CRITICAL.** Driver Station a pierdut gamepad-ul | cablul / hub-ul USB de la Driver Hub |
| `imu.yaw-frozen` | direcția IMU nu se schimbă nici cu un bit timp de 5 s, cât motoarele merg | IMU (I2C) s-a blocat: reporniți robotul, verificați cablurile |
| `brownout` | **CRITICAL.** bateria sub 8 V | hub-urile repornesc pe la 7 V: schimbați bateria, verificați conectorii |
| `battery-low-at-start` | la INIT (robotul stă) bateria e sub 12,4 V | încărcați-o sau puneți alta înainte de meci |
| `slow-loop` | ciclul durează peste 50 ms o secundă întreagă | ceva încetinește: camera, prea multe citiri de senzori, `sleep` |
| `loop-pause` | un singur ciclu peste 150 ms | o smucitură pe care o simte pilotul: un apel blocant, `sleep`, garbage collection |

Reguli proprii:

```java
bb.rule("intake-jam").forTime(0.25).show("intake.amps", "intake.vel")
  .when(r -> r.get("intake.amps") > 4 && Math.abs(r.get("intake.vel")) < 50);
bb.notReachingRule("lift-slow", "lift.target", "lift.inches", 1.0, 1.5);   // nu ajunge la țintă
bb.frozenRule("front.mm", 3.0, "fl.power");     // un senzor de distanță care se uită spre teren
bb.encodersReset();                             // resetați voi encoderele (nu e o repornire a hub-ului)
```

### Comparația cu antrenamentele trecute

După `bb.stop()`, raportul compară sesiunea aceasta cu mediana celor trecute (cu același nume):

```
lift.amps peak 5.2 vs 3.4 (+53%)
battery A: 0.142 ohm watch it; usually 0.098 ohm (+45%) WORSE THAN USUAL
loop 24.1 ms avg: vision 14.2 ms (59%, max 31.0), drive 3.1 ms (13%), other 6.8 ms (28%)
```

Așa uzura unui mecanism se vede **înainte** să se strice.

**Unde se pierde timpul ciclului:**

```java
bb.section("vision"); vision.update();
bb.section("drive");  drive();
bb.update();
```

### Unde vedeți rezultatele

- **Driver Hub**: ultimele avertismente direct în telemetrie.
- **Pagina web**: pe un laptop sau telefon conectat la Wi-Fi-ul robotului, deschideți `http://192.168.43.1:8099/`
  (Driver Hub arată singur adresa). Alegeți o înregistrare: pagina spune în cuvinte simple ce e în regulă,
  ce trebuie verificat și cum. Graficele sunt dedesubt. Limba: EN, RO, RU.
- **FTC Dashboard**: dacă îl aveți instalat, toate semnalele apar singure acolo ca grafice.
- **AdvantageScope**: de pe pagina web, fiecare sesiune se descarcă ca `.wpilog` și se deschide în
  [AdvantageScope](https://github.com/Mechanical-Advantage/AdvantageScope).
- **Fișiere** pe Control Hub, în `/sdcard/FIRST/asa/`:

| Fișier | Ce conține |
|---|---|
| `<data>_<nume>.csv` | toate semnalele, la fiecare ciclu |
| `.events.csv` | evenimente și avertismente, cu ora |
| `.summary.csv` | media și vârful fiecărui semnal |
| `.report.txt` | de câte ori a pornit fiecare regulă + comparația cu cele trecute |
| `.crash.txt` | la o cădere: eroarea, unde exact, ultimele valori ale tuturor semnalelor |
| `batteries.csv` | istoricul fiecărei baterii |

### E sigur în meci?

Da. Scrierea pe disc se face într-un fir separat. Dacă un senzor dă eroare, valoarea lui se scrie ca NaN. Dacă
discul e plin, rândurile se sar. Black Box nu aruncă niciodată o excepție în ciclul vostru. Dacă fișierele nu se
scriu, pe Driver Hub apare `BlackBox NOT SAVING: …`, iar robotul merge mai departe.

Costul în timp: pozițiile și vitezele se citesc cu un singur bulk read pe hub (Black Box pune `AUTO` dacă era
`OFF`). Curentul motoarelor se citește o dată la 100 ms, cel mult 2 citiri lente pe ciclu. `update()` în sine
durează microsecunde.

---

## 4. SysId: constante pentru lift, braț, flywheel, șasiu

### Pasul 1. Descrieți mecanismul

Copiați `src/org/asa/tools/examples/ExampleLiftSysId.java`, redenumiți-l, scrieți numele motoarelor din
configurație și ștergeți `@Disabled`:

```java
@TeleOp(name = "ASA SysId Lift", group = "ASA")
public class LiftSysId extends AsaSysIdOpMode {
    static final double IN_PER_TICK = 0.0177;             // circumferința tamburului / tick-uri pe rotație

    protected SysIdMechanism mechanism() {
        DcMotorEx lift = hardwareMap.get(DcMotorEx.class, "lift");
        lift.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);  // putere brută, fără PID-ul încorporat
        lift.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        return SysIdMechanism.elevator("lift")               // .arm(...) pentru braț, .simple(...) flywheel/șasiu
            .motor(lift::setPower)
            .position(() -> lift.getCurrentPosition() * IN_PER_TICK)
            .velocity(() -> lift.getVelocity() * IN_PER_TICK)   // ACELEAȘI unități, pe secundă
            .battery(Ftc.battery(hardwareMap))
            .softLimits(1, 28)                               // o marjă față de opritoarele fizice
            .maxPower(0.7);
    }
}
```

| Model | Pentru ce |
|---|---|
| `simple` | flywheel, intake, glisieră orizontală, șasiu |
| `elevator` | lift vertical (are gravitație, kG) |
| `arm` | braț pe ax; unghiul în radiani **față de orizontală** |

### Pasul 2. Rulați pe Driver Hub

Alegeți **ASA SysId Lift** → INIT (robotul nu se mișcă) → START. Gamepad 1:

| Buton | Acțiune |
|---|---|
| stick stânga | mutați mecanismul între teste (se oprește la limitele soft) |
| dpad ↑/↓ | alegeți testul |
| **A** | porniți testul (ordinea: QF, QR, DF, DR) |
| **B** | opriți (și STOP oprește mereu motoarele) |
| **X** | calculați și salvați |
| **RB** | verificare: doar PID față de feedforward + același PID |
| **Y** | resetați tot |

Testele: două accelerări lente (în sus și în jos) și două porniri bruște (în sus și în jos). Dacă mecanismul pleacă
în direcția greșită, SysId oprește imediat motorul și spune ce nu e în regulă.

### Pasul 3. Constantele se pun singure

După **X**, cu rezultat GOOD sau OK, constantele se salvează în `/sdcard/FIRST/asa/sysid/lift.ff`. În codul vostru
scrieți:

```java
static final Feedforward LIFT_FF = Feedforward.load("lift", Feedforward.elevator(0.41, 0.76, 0.117, 0.018));

double volts = LIFT_FF.calculate(profileVel, profileAcc) + kP * (target - pos);
lift.setPower(Feedforward.toPower(volts, battery));
```

`load` ia ce a măsurat SysId. Ați măsurat din nou mecanismul? Constantele noi merg de la următorul INIT, nu trebuie
copiat nimic. Numerele din paranteză sunt rezerva, dacă fișierul lipsește (de exemplu, pe alt robot). Un rezultat
POOR nu ajunge niciodată în fișier. `LIFT_FF.source` arată de unde vin constantele.

### Pasul 4. Arătați că e mai bine (RB)

Robotul merge pe rând doar cu PID, apoi cu feedforward + același PID, și arată diferența: eroarea de urmărire,
depășirea țintei, timpul de stabilizare. E o dovadă gata făcută pentru Control Award.

### Masa robotului

Rulați SysId pe șasiu mergând drept (`ExampleDrivetrainSysId.java`, e nevoie de ~3 m de podea). Apoi:

```java
double kg = MotorSpec.GOBILDA_5203.robotMassKg(4, result.kV, result.kA);   // sau MotorSpec.REV_HD_HEX
```

Precizia e de aproximativ ±15%. E o verificare încrucișată, cântarul e mai precis.

### Dacă rezultatul e POOR

| Mesaj | Ce faceți |
|---|---|
| `did not move` | măriți `maxPower`, verificați dacă e ceva blocat și dacă motorul e conectat |
| `moved against the command` | direcția motorului sau a encoderului e inversată |
| `velocity does not match position` | viteza trebuie să fie în aceleași unități ca poziția, **pe secundă** |
| `ignored N glitched samples` | au fost erori de encoder, au fost aruncate. Dacă se întâmplă des, verificați cablul |
| `poor fit` | joc mecanic, cureaua alunecă, cursă prea scurtă |

---

## 5. Cum funcționează pe dinăuntru

**Black Box.** La fiecare ciclu, `update()` citește toate semnalele. Cele rapide (poziții, puteri) la fiecare
ciclu, cele lente (curent, IMU, temperatură) după un program: întâi cel care a așteptat cel mai mult, cel mult 2 pe
ciclu. Rândul intră într-o coadă, iar fișierul îl scrie un fir separat. Fiecare regulă e o condiție plus un timp de
menținere. Avertismentul apare o singură dată, când condiția ține destul, și nu se repetă până nu dispare condiția.

- **Rezistența bateriei** se calculează din mersul obișnuit. Fiecare salt de curent ΔI dă o cădere de tensiune ΔU,
  iar R = −ΔU/ΔI. Se ia mediana pe multe salturi, așa că valorile izolate nu deranjează.
- **Supraîncălzirea** e un model termic de ordinul întâi: curentul la pătrat, netezit cu o constantă de ~2 minute.
  Un blocaj scurt nu îl ridică, o suprasarcină lungă îl ridică.
- **Frecarea unei roți.** Dacă toate roțile primesc aceeași putere și se învârt cu viteză asemănătoare, le trebuie
  un curent asemănător. Roata care cere de două ori mai mult e frânată.

**SysId** găsește constantele cu metoda celor mai mici pătrate în formă integrală: accelerația nu se derivă din
viteza cu zgomot, așa că zgomotul se mediază în loc să se amplifice. Întârzierea cu care hub-ul REV mediază viteza
este măsurată și compensată. Erorile de encoder și jocul mecanic sunt detectate și aruncate. Fiecare constantă are o
incertitudine, iar GOOD se dă doar dacă ea e mică. Datele proaste dau un mesaj clar în loc de cifre greșite.

---

## 6. Cât vă puteți baza pe el

Testele rulează automat pe GitHub Actions la fiecare modificare a codului (Monte Carlo pe un eșantion mai mic;
cifrele de mai jos sunt rulările complete):

| Verificare | Rezultat |
|---|---|
| Black Box, 9000 de meciuri aleatorii (2–8 motoare, unele fără encoder, accelerări, schimbări de sens, lift ținut sus, baterie care scade, ciclu neregulat) cu defecte introduse la întâmplare | 10 163 din 10 165 prinse (cele 2 ratate: blocaje de 0,6 s, la limita de detecție), **0 alarme false** |
| Fiecare detector nou | își prinde defectul exact o dată și tace la un mers sănătos asemănător |
| SysId, 8400 de mecanisme aleatorii, un sfert cu greșeli de configurare | toate greșelile de configurare prinse; rezultate sigure dar greșite: 0,1% (cazuri extreme) |
| Verificarea (RB), 1000 de lifturi și brațe aleatorii | 1000/1000 |
| 10 minute de înregistrare, 60 000 de rânduri × 40 de semnale | niciun rând pierdut, memoria nu crește |
| Compatibilitate | compilează cu FTC SDK oficial 12.0.0 și 9.2.0; doar API Java din Android 7.1 (Control Hub); D8 |

**Sincer:** bibliotecile sunt verificate în simulare și cu SDK-ul real, dar **încă nu pe un Control Hub fizic**.
Pragurile (5 A, 12,4 V, de 2 ori curentul) sunt estimări bune din datasheet-uri; un robot real poate cere ajustări.
Dacă le rulați pe robot, vă rugăm
[scrieți-ne rezultatul](https://github.com/stanchevicid-hash/asa-blackbox/issues/new?template=robot-result.yml), bun
sau rău.

---

## 7. Întrebări frecvente

**Unde găsesc programele pe Driver Hub?** În lista TeleOp, grupul ASA. Dacă nu sunt acolo, codul nu s-a încărcat:
compilați proiectul din nou.

**Black Box încetinește robotul?** Nu. Citirile grele sunt împărțite în timp, iar scrierea se face într-un fir
separat.

**Motor fără encoder: vor fi alarme false `encoder-lost`?** Nu. Regula pornește doar dacă encoderul acelui motor a
arătat măcar o dată mișcare.

**Șasiul nu e recunoscut pentru `drags`?** Roțile sunt găsite după nume: `frontLeft`, `left_front_drive`, `lf`, `fl`,
`backRight` etc. Dacă numele sunt altele, adăugați-le voi: `bb.imbalanceRule("a", "b", "c", "d")`.

**Cum deschid pagina cu rezultate?** Conectați un laptop sau un telefon la Wi-Fi-ul robotului și deschideți
`http://192.168.43.1:8099/`. Adresa apare și în telemetrie.

Făcut de echipa **ASA, FTC 28284**. Licență MIT, gratuit pentru toate echipele.

---

**Urmăriți ASA:** [Instagram](https://www.instagram.com/asa.ftc_md/) · [TikTok](https://www.tiktok.com/@ftc.team.asa) · ⭐ dați o stea repository-ului dacă v-a ajutat.
**Scrieți-ne** ([formularul de feedback](https://github.com/stanchevicid-hash/asa-blackbox/issues/new?template=robot-result.yml)):
ce a mers, ce nu, ce lipsește. Din fiecare mesaj facem următoarea versiune mai bună.
