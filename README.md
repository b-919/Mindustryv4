![Imgur](https://i.imgur.com/w4N0yhv.png)

This is a Fan made project not aproved by the main dev, non-profit, the only objective is to edit the code of this version and have fun!

### The Never Ending TO-DO list
* Full Refactor to the Biomass - finish it
* Enhacement to game visuals
* Update Campaign system
* Add Tank Units and LegUnits
* More QoL
* Balance Logistic Drones
* More units, more content and bugfix

### Building

First, make sure you have JRE 17 to run and JDK 17 installed for make your builds
Open a terminal in the root directory, and run the following commands:

#### Windows

_Running:_ `gradlew desktop:run`  
_Building:_ `gradlew desktop:dist`

#### Linux

_Running:_ `./gradlew desktop:run`  
_Building:_ `./gradlew desktop:dist`

#### For Server Builds...

Server builds are bundled with each released build (in Releases). If you'd rather compile on your own, replace 'desktop' with 'server' i.e. `gradlew server:dist`.

---

Gradle may take up to several minutes to download files. Be patient. <br>
After building, the output .JAR file should be in `/desktop/build/libs/desktop-release.jar` for desktop builds, and in `/server/build/libs/server-release.jar` for server builds.

### CLaJ (Copy Link and Join)

This fork includes a v4-native CLaJ implementation. It lets a host create a room on a relay
and share a link such as `claj://relay.example:7000/AbCdEf12345`; friends can join with
**Play -> Join Game -> Join via CLaJ**. The host does not need to forward the game port.

#### Running a relay

From the repository root:

`gradlew runRelay -Pport=7000`

Or build a standalone relay jar:

`gradlew relayJar
java -jar build/release/claj-relay.jar 7000`

The relay jar is self-contained and only needs Java 17. Open TCP port `7000` (or the port
you chose) on the relay machine. The relay only forwards traffic; the host and clients
connect to it outbound.

#### Using a room

1. Host a normal game.
2. Open the pause menu and choose **Manage CLaJ Room**.
3. Enter the relay address (`host:port`), optionally set a 4-digit password, and press
   **Create Room**.
4. Press **Copy Link** and send it to your friends.
5. Friends open **Join Game -> Join via CLaJ**, paste the link (and password if needed),
   and press **OK**.

Dedicated servers can also use the console command `claj <relay:port> [password]` to
create a room and print the link, and `claj-stop` to close it.

> **Compatibility note:** v4 uses its own length-framed CLaJ transport because its
> networking stack is KryoNet-based. The protocol and link format follow the modern
> `xpdustry/claj` design, but this implementation is **not wire-compatible** with the
> official CLaJ relay. Run the relay included with this project for v4 rooms.

### Extra Credits:
[Twcash](https://github.com/Twcash) creator of the Mass Sprites/Textures.<br>
[xpdustry](https://github.com/xpdustry) Original creator of CLaJ feature.
### ORIGINAL Downloads


| [![](https://static.itch.io/images/badge.svg)](https://anuke.itch.io/mindustry)    |    [![](https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png)](https://play.google.com/store/apps/details?id=io.anuke.mindustry)   |    [![](https://fdroid.gitlab.io/artwork/badge/get-it-on.png)](https://f-droid.org/packages/io.anuke.mindustry)	| [![](https://flathub.org/assets/badges/flathub-badge-en.svg)](https://flathub.org/apps/details/com.github.Anuken.Mindustry)  
|---	|---	|---	|---	|
