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

This fork includes CLaJ support using the official public relay network. A host creates a
room on a relay and shares a link such as
`claj://n4.xpdustry.com:50000/AbCdEf12345`; friends can join with
**Play -> Join Game -> Join via CLaJ**. The host does not need to forward the game port.

#### Using a room

1. Host a normal game.
2. Open the pause menu and choose **Manage CLaJ Room**.
3. Press **Browse public relays** and pick a node, or enter a relay address
   (`host:port`). Optionally set a 4-digit password, then press **Create Room**.
4. Press **Copy Link** and send it to your friends.
5. Friends open **Join Game -> Join via CLaJ**, paste the link (and password if needed),
   and press **OK**.

Both host and client connect outbound to the relay; TCP and UDP must be reachable on the
relay's port. The link format is `claj://host:port/room-id`.

Relay support targets the current CLaJ 2.4.x control protocol. The relay's major version is
discovered automatically, but future incompatible CLaJ protocol revisions may require
updating the bundled `com.xpdustry.claj.common` sources.

Dedicated servers can also use the console command `claj <relay:port> [password]` to create
a room and print the link, and `claj-stop` to close it.

> **Compatibility note:** CLaJ only tunnels the connection; v4 and modern clients will never be
> compatibles even if server is detected in modern version, do not try.

### Extra Credits:
[Twcash](https://github.com/Twcash) creator of the Mass Sprites/Textures.<br>
[xpdustry](https://github.com/xpdustry) Original creator of CLaJ feature.
### ORIGINAL Downloads


| [![](https://static.itch.io/images/badge.svg)](https://anuke.itch.io/mindustry)    |    [![](https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png)](https://play.google.com/store/apps/details?id=io.anuke.mindustry)   |    [![](https://fdroid.gitlab.io/artwork/badge/get-it-on.png)](https://f-droid.org/packages/io.anuke.mindustry)	| [![](https://flathub.org/assets/badges/flathub-badge-en.svg)](https://flathub.org/apps/details/com.github.Anuken.Mindustry)  
|---	|---	|---	|---	|
