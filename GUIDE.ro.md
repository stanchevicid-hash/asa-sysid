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

**Cel mai simplu:** descărcați din release `asa-blackbox-onbot.zip`, dezarhivați și încărcați toate cele 17 fișiere în
folderul obișnuit `teamcode` din OnBot Java, fără subfoldere. Apăsați Build. Dacă prima compilare nu reușește
(„bad source file”), apăsați Build încă o dată.

Numele tuturor fișierelor, în afară de `BlackBox` și `FtcBlackBox`, încep cu `Asa`, așa că încărcarea nu înlocuiește
niciodată fișierele voastre. **Aveați versiunea 1.4?** Mai întâi ștergeți fișierele ei vechi `Rule`, `Live`,
`Recorder`, `Ftc`, `Dashboard`, `WpiLog`, `BatteryHealth`, `CenterOfGravity` (dacă nu sunt fișierele voastre cu aceleași nume).

**Apoi, o singură dată: Restart Robot** (meniul ⋮ de pe Driver Hub → Restart Robot). Asta pornește înregistrarea
automată a meciurilor (secțiunea 3). Până atunci pornește prima dată când rulați programul „ASA Black Box”.

**Sau cu foldere:**

1. Conectați-vă la Wi-Fi-ul robotului și deschideți `http://192.168.43.1:8080` → **OnBot Java**.
2. Creați folderele `org/asa/tools/core`, `org/asa/tools/ftc`, `org/asa/tools/blackbox` (și `org/asa/tools/sysid`
   pentru SysId).
3. Încărcați în fiecare folder fișierele `.java` din folderul cu același nume din `src/` al repository-ului.
   Apăsați Build.

Merge cu FTC SDK de la 9.x la 12.x. Biblioteca a fost compilată cu SDK-urile oficiale 12.0.0 și 9.2.0.

### Verificare după instalare (10 minute)

Parcurgeți pașii în ordine. Dacă la fiecare pas vedeți ce scrie în dreapta, totul funcționează.

| # | Ce faceți | Ce trebuie să vedeți |
|---|---|---|
| 1 | **Restart Robot**, apoi alegeți **ASA Black Box** și apăsați INIT (încă nu START) | 🟢 „Fiecare meci se înregistrează (conectat la pornire)” și rândul „REZULTATE: … :8099” |
| 2 | Telefon sau laptop pe Wi-Fi-ul robotului, deschideți adresa de la pasul 1 | pagina cu logo-ul ASA, sus un punct verde „conectat” |
| 3 | În ASA Black Box: **RB** până la „Test înregistrare”, START. Robotul nu se mișcă, ~16 s | trei rânduri verzi: ciclul cu înregistrare aproape ca fără ea, cam câte măsurători se așteptau, encoderele citite la toate motoarele |
| 4 | Ridicați robotul. ASA Black Box → „Verificare robot”, START, **Y** | la fiecare motor un rând verde „OK … encoderul numără în sus”. Roșu sau galben e o problemă reală: fir, port sau direcție |
| 5 | Porniți **TeleOp-ul vostru** ca de obicei: INIT, START, conduceți un minut, STOP | pe pagină, în timpul condusului, „Acum” cu grafice; după STOP se deschide singură înregistrarea cu numele programului |
| 6 | Din nou INIT în ASA Black Box | „Ultima înregistrare: <TeleOp-ul vostru>, ora” |
| 7 | Tot acolo **Y** → „Înregistrarea automată e oprită”, porniți TeleOp-ul vostru | nu apare o înregistrare nouă. Apoi porniți-o la loc (**Y**) |

Culorile la pasul 1: 🟡 „s-a conectat abia acum” — apăsați o dată Restart Robot și repetați pasul 1. Un rând roșu
despre pagină înseamnă că portul e ocupat de alt program: reporniți robotul.

Culorile la pasul 3: 🟡 „puțin mai lent” — puteți conduce, dar urmăriți dacă robotul se comportă altfel; 🔴 „clar mai
lent” — opriți înregistrarea automată (**Y**), restul funcționează și fără ea, și trimiteți-ne o poză a ecranului.

**Trimiteți-ne** ([formular](https://github.com/stanchevicid-hash/asa-blackbox/issues/new?template=robot-result.yml)) o
poză cu rezultatul pasului 3 și tot ce a ieșit roșu. Așa aflăm că biblioteca merge nu doar pe robotul nostru.

---

## 2. Programul gata făcut pe Driver Hub (fără cod)

După instalare, în lista de OpMode-uri de pe Driver Hub, grupul **ASA**, apare un singur program: **ASA Black Box**.
Dacă aveați „ASA Health Check” și „ASA Center of Gravity”, ele dispar singure când încărcați fișierele noi peste cele
vechi.

**La INIT (înainte de START):**

| Buton | Ce face |
|---|---|
| **LB / RB** | alegeți modul: **Verificare robot**, **Centrul de greutate**, **Test înregistrare** (secțiunea 1) sau **Șterge ASA** (secțiunea 8) |
| **X** | limba: EN → RU → RO. Pagina web va fi în aceeași limbă |
| **Y** | înregistrarea automată a meciurilor: PORNIT / OPRIT (secțiunea 3) |
| dpad | setările modului ales (vezi mai jos) |

Programul ține minte modul și limba până data viitoare. Pe ecranul INIT se vede dacă fiecare meci se înregistrează, ultima înregistrare și adresa paginii cu rezultate.

### Modul „Verificare robot”: înainte de meci

| Când | Buton | Ce face |
|---|---|---|
| INIT | dpad ←/→ | ce baterie e pusă (A…H): starea fiecărei baterii este ținută minte |
| după START | **Y** | **testează singur toate motoarele**: pe rând, putere 0,3, câte 1,5 s fiecare. **B** = stop |
| | dpad ↑/↓ | sau alegeți un motor manual… |
| | **țineți A / B** | …și îl rotiți cu puterea +0,3 / −0,3, **doar cât timp țineți butonul** |
| | **X** | terminați și vedeți raportul |

Pe ecran apar:

- fiecare hub: dacă răspunde, temperatura, tensiunea, **versiunea de firmware**;
- pentru fiecare motor: dacă are curent, dacă encoderul numără și în ce direcție;
- istoricul bateriilor: rezistența internă și care baterie a devenit mai slabă decât era.

Cât timp durează verificarea, pagina web arată grafice live. Când apăsați X (sau testul automat se termină), pagina
deschide singură raportul.

**Înainte de verificare ridicați robotul sau faceți loc: motoarele se vor învârti.**

### Modul „Centrul de greutate”

Motoarele nu se mișcă. E nevoie doar de IMU-ul hub-ului (în configurație trebuie să se numească `imu`).

1. INIT: cu dpad ↑/↓ setați **ampatamentul** (distanța față-spate dintre roți), cu ←/→ **ecartamentul**
   (distanța stânga-dreapta), în centimetri, de la punctul de contact al roții cu podeaua la celălalt. START.
2. Cinci pași. La fiecare puneți robotul cum scrie pe ecran, așteptați să nu se mai clatine („stabil”) și apăsați **A**:
   - drept pe podea;
   - pe roțile din spate: ridicați partea din față până când robotul stă singur în echilibru;
   - pe roțile din față;
   - pe roțile din dreapta;
   - pe roțile din stânga.
3. Rezultatul pe Driver Hub: înălțimea centrului de greutate, poziția lui față-spate și stânga-dreapta, la ce
   accelerație se răstoarnă robotul la frânare, accelerare și lateral, și pe ce pantă maximă poate sta.
4. **Desenul pe pagina web** (se deschide singur): văzut de sus și văzut din dreapta, punctul roșu e centrul de
   greutate, distanțele în cm, liniile punctate sunt liniile de răsturnare cu unghiurile lor. Sub desen, un semafor
   pentru fiecare limită: 🟢 roțile alunecă înainte ca robotul să se răstoarne; 🟡 se poate răsturna la o oprire bruscă
   sau o lovitură; 🔴 se răstoarnă ușor. Desenul se poate descărca (SVG) pentru portofoliu.

**B** = un pas înapoi, **Y** = de la început. Măsurați încă o dată cu liftul ridicat: veți vedea cât de mult ridică
liftul centrul de greutate. Rezultatul se salvează în `/sdcard/FIRST/asa/<data>_cog.txt`.

---

## 3. Fără cod: fiecare meci se înregistrează singur

Instalați biblioteca, reporniți robotul o dată și gata. Când rulează **orice** program (TeleOp-ul vostru în Java,
Autonomous, chiar și un program în Blocks), Black Box îl înregistrează singur în fundal, fără nimic adăugat în cod:
motoarele (putere, poziție, viteză, curent), servo-urile, bateria, hub-urile și ambele gamepad-uri (în Autonomous fără
gamepad-uri), cu toate avertismentele. IMU-ul și ceilalți senzori nu sunt citiți automat: la INIT asta le-ar încurca
inițializarea. Îi înregistrează cele 4 rânduri din secțiunea 4. Înregistrarea poartă numele programului, așa că „TeleOp Main” se compară cu rulările anterioare.

Cum nu vă încurcă în meci:

- nu schimbă setările hub-urilor (caching, modurile motoarelor). Toate encoderele unui hub se citesc dintr-o **singură**
  cerere comună: de 20 de ori pe secundă cu Control Hub singur, de 10 ori cu un Expansion Hub în plus. Curentul
  motoarelor (citirea lentă) la fiecare 0,4 s. Firul înregistrării are prioritate mai mică decât programul vostru;
- orice eroare oprește înregistrarea, nu robotul;
- dacă programul folosește deja Black Box în propriul cod (4 rânduri, secțiunea 4), îl înregistrează doar acel cod,
  fără copie;
- INIT și apoi STOP fără START nu se păstrează. Pe Control Hub rămân ultimele 300 de înregistrări, cele mai vechi se
  șterg singure (rezultatele centrului de greutate și istoricul bateriilor nu se șterg niciodată).

Oprire sau pornire: programul **ASA Black Box**, butonul **Y** la INIT. Fără codul vostru înregistrarea nu vede timpul
ciclului, valorile voastre (ținte, stări) și evenimentele. Pentru ele adăugați cele 4 rânduri din secțiunea 4.

**La competiții:** înregistrarea merge singură, nu are nevoie de telefon. În timpul meciului nu conectați telefoane sau
laptopuri la Wi-Fi-ul robotului: pe el Driver Station comandă robotul (și regulile FTC o interzic). Uitați-vă la
înregistrări în boxe, după meci. Cât timp programul merge, pagina nu trimite fișiere mari, ca să nu ocupe Wi-Fi-ul.

---

## 4. Black Box în propriul TeleOp sau Autonomous

Ajung câteva rânduri în OpMode:

```java
BlackBox bb = BlackBox.create("teleop");                 // numele sesiunii
FtcBlackBox.attachAll(bb, hardwareMap);                   // TOT din configurație + reguli + pagina web
FtcBlackBox.attachGamepads(bb, gamepad1, gamepad2);       // stick-urile piloților pe aceeași axă de timp
bb.battery("A");                                          // ce baterie e pusă (opțional)

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
- **Pagina web** pornește singură în orice program cu Black Box. Conectați un telefon sau un laptop la Wi-Fi-ul
  robotului și deschideți `http://192.168.43.1:8099/` (Driver Hub scrie adresa pe rândul „REZULTATE”). Deschideți-o o
  dată și lăsați-o deschisă: pagina urmărește singură robotul:
  - **programul a pornit** → fila **„Acum”**: grafice live din ultimele 60 s (baterie, curenții motoarelor, timpul
    ciclului; bifați orice semnal) și avertismentele imediat ce apar;
  - **programul s-a oprit** → se deschide înregistrarea lui: în cuvinte simple, ce e în regulă, ce trebuie verificat și
    cum. Graficele sunt dedesubt;
  - **deschisă mai târziu** → arată ultima înregistrare.

  Fiecare problemă are eticheta **„în cod”** sau **„în robot”**. Înregistrarea păstrează ce a făcut pilotul
  (stick-urile), ce a comandat programul (puterea motoarelor) și ce a făcut robotul (encodere, curent), iar pagina le
  compară. Stick apăsat fără putere pe șasiu, puterea care sare între + și − de multe ori pe secundă, un encoder care
  numără invers față de putere sunt **în cod**. Fără curent, motor blocat, frecare, baterie slabă sunt **în robot**.

  Graficele sunt exacte: fiecare pixel desenează minimul și maximul tuturor punctelor care cad în el, așa că și un vârf
  de un singur ciclu (stall, cădere de baterie) se vede pe o înregistrare de 4 minute. Treceți cu mouse-ul peste grafic
  ca să vedeți timpul și valoarea. Limba paginii e cea aleasă pe Driver Hub (X) și se schimbă cu RU / RO / EN.
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

## 5. SysId: constante pentru lift, braț, flywheel, șasiu

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

## 6. Cum funcționează pe dinăuntru

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

## 7. Cât vă puteți baza pe el

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

## 8. Cum scoateți complet ASA

ASA nu schimbă nimic în codul echipei, în configurația robotului sau în SDK. Tot ce creează sunt fișierele ei din
OnBot Java și un singur folder pe Control Hub, `FIRST/asa`.

**Cu un singur buton:** ASA Black Box → modul **„Șterge ASA”** → START → țineți **A + B** 3 secunde → STOP → în OnBot
Java apăsați **Build**. Programul ASA Black Box dispare din listă.

Ce șterge butonul, și doar asta:
- înregistrările, rezultatele și setările (folderul `FIRST/asa`);
- fișierele bibliotecii din OnBot Java: în folderul `teamcode` doar cele care încep cu rândul `// ASA Black Box library file`
  (chiar dacă aveți propriul `Recorder.java` sau `Rule.java`, nu e atins), și fișierele Black Box din folderele `org/asa/tools`;
- dacă un fișier are nume ASA, dar nu are marcajul și pare ASA, butonul nu șterge nimic și îl arată: verificați-l voi;
- înregistrarea și pagina de rezultate se opresc imediat, încă înainte de Build.

Ce nu face butonul:
- dacă codul vostru folosește Black Box (cele 4 rânduri din secțiunea 4), **nu șterge nimic** și arată din ce fișiere
  să-l scoateți: altfel proiectul vostru nu s-ar mai compila;
- dacă e instalat ASA SysId, fișierele comune rămân și SysId merge în continuare;
- fișierele dintr-o versiune veche, fără marcaj, nu le șterge, ci le numește ca să le verificați voi.

**Manual.** OnBot Java: ștergeți cele 17 fișiere `AsaAutoRecorder` `AsaBatteryHealth` `AsaBlackBoxOpMode` `AsaCenterOfGravity` `AsaCenterOfGravityMath` `AsaDashboard` `AsaFtc` `AsaHealthCheck` `AsaLive` `AsaRecorder` `AsaRemover` `AsaRule` `AsaWeb` `AsaWebPage` `AsaWpiLog` `BlackBox` `FtcBlackBox` (`.java`), apăsați Build. Android Studio: ștergeți folderul
`TeamCode/src/main/java/org/asa` (dacă acolo e și ASA SysId, lăsați `core`, `ftc/AsaWeb*`, `ftc/Ftc.java`, `sysid`),
instalați din nou aplicația. Apoi **Restart Robot**.

Vreți doar s-o opriți, nu s-o scoateți? ASA Black Box → **Y** → „Înregistrarea automată e oprită”. Atunci la
pornirea robotului ASA nu pornește nimic: nici înregistrare, nici pagină.

---

## 9. Întrebări frecvente

**Unde găsesc programul pe Driver Hub?** În lista TeleOp, grupul ASA, „ASA Black Box”. Dacă nu e acolo, codul nu s-a
încărcat: compilați proiectul din nou (în OnBot uneori trebuie apăsat Build de 2–3 ori).

**Au rămas vechile „ASA Health Check” și „ASA Center of Gravity”?** Atunci fișierele noi `AsaHealthCheck.java` și
`AsaCenterOfGravity.java` nu s-au încărcat peste cele vechi. Încărcați-le din nou și apăsați Build.

**Meciul nu s-a înregistrat?** Înregistrările apar doar dacă programul a ajuns la START. Dacă după instalare robotul
nu a fost repornit, rulați o dată „ASA Black Box” sau apăsați Restart Robot. Verificați că în „ASA Black Box” la INIT
scrie „Înregistrare meciuri (Y): ● PORNIT”.

**Înregistrarea în fundal încurcă robotul?** N-ar trebui (vezi secțiunea 3), dar verificați pe robotul vostru: dacă
ciclul devine mai lent cu ea, opriți-o cu Y în „ASA Black Box” și scrieți-ne: e important pentru toate echipele.

**Black Box încetinește robotul?** Nu. Citirile grele sunt împărțite în timp, iar scrierea se face într-un fir
separat.

**Motor fără encoder: vor fi alarme false `encoder-lost`?** Nu. Regula pornește doar dacă encoderul acelui motor a
arătat măcar o dată mișcare.

**Șasiul nu e recunoscut pentru `drags`?** Roțile sunt găsite după nume: `frontLeft`, `left_front_drive`, `lf`, `fl`,
`backRight` etc. Dacă numele sunt altele, adăugați-le voi: `bb.imbalanceRule("a", "b", "c", "d")`.

**Cum deschid pagina cu rezultate?** Conectați un laptop sau un telefon la Wi-Fi-ul robotului și deschideți
`http://192.168.43.1:8099/`. Adresa apare și în telemetrie. Pagina nu se deschide singură în browser: Android nu lasă
robotul să pornească browserul pe alt dispozitiv. Dar ajunge s-o deschideți o dată: apoi trece singură între graficele
live și înregistrări. E comod s-o puneți la marcaje sau pe ecranul principal al telefonului.

**Pagina scrie „fără conexiune”?** Telefonul s-a deconectat de la Wi-Fi-ul robotului (des trece singur pe o rețea cu
internet). Reconectați-l și pagina continuă singură.

Făcut de echipa **ASA, FTC 28284**. Licență MIT, gratuit pentru toate echipele.

---

**Urmăriți ASA:** [Instagram](https://www.instagram.com/asa.ftc_md/) · [TikTok](https://www.tiktok.com/@ftc.team.asa) · ⭐ dați o stea repository-ului dacă v-a ajutat.
**Scrieți-ne** ([formularul de feedback](https://github.com/stanchevicid-hash/asa-blackbox/issues/new?template=robot-result.yml)):
ce a mers, ce nu, ce lipsește. Din fiecare mesaj facem următoarea versiune mai bună.
