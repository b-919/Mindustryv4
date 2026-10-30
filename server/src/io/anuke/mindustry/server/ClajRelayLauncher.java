package io.anuke.mindustry.server;

import io.anuke.mindustry.claj.ClajRelayServer;

/**
 * Standalone CLaJ relay launcher.
 *
 * <p>Usage: {@code java -jar claj-relay.jar [port]}
 * or {@code ./gradlew runRelay -Pport=7000}.</p>
 */
public class ClajRelayLauncher{
    public static void main(String[] args) throws Exception{
        int port = 7000;
        if(args.length > 0){
            try{
                port = Integer.parseInt(args[0]);
            }catch(NumberFormatException e){
                System.err.println("Invalid port: " + args[0]);
                System.exit(1);
            }
        }

        if(port < 1 || port > 0xFFFF){
            System.err.println("Invalid port: " + port);
            System.exit(1);
        }

        ClajRelayServer relay = new ClajRelayServer(port);
        relay.start();
        Runtime.getRuntime().addShutdownHook(new Thread(relay::stop, "CLaJ Relay Shutdown"));
        System.out.println("CLaJ relay is running. Press Ctrl+C to stop.");

        Thread.currentThread().join();
    }
}
