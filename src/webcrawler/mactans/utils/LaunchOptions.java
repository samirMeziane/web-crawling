package webcrawler.mactans.utils;

import java.io.IOException;
import java.util.Scanner;

import webcrawler.mactans.server.Server;

/**
 * This class handles the server's Command Line Interface.
 *
 */
public class LaunchOptions {

	/**
	 * Prints the art and commands list to the console.
	 */
	public static final void greetings() {
		System.out.println(Art.spider + Art.codeName);
		commandsList();
	}

	/**
	 * Prints the list of commands to the console.
	 */
	public static final void commandsList() {
		System.out.format("Commands list: \n\r " + "help:\t\t View this list. \n\r "
				+ "capacity:\t Change the threshold (number of processed URLs) after \n\t\t which an exploration stops. (max. %s, current %s) \n\r "
				+ "reload:\t Force reload the save file. \n\r " + "shutdown:\t Stop the server. \n\r "
				+ "spawn:\t\t Change the number of spawned explorers per client. (max. %s, current %s) \n\r "
				+ "timeout:\t Time in minutes after which a client is automatically \n\t\t disconnected. (default %s) \n\r",
				Server.MAXIMUM_CAPACITY, Server.capacity, Server.MAXIMUM_SPAWN_RATE, Server.spawnRate,
				Server.DEFAULT_TIMEOUT / 60000);
	}

	/**
	 * Initiates the Command Line Interface for the server and listens for input.
	 * 
	 * @param server
	 * @param serverThread
	 * @throws IOException
	 */
	public static final void cliServerManagement(Server server, Thread serverThread) throws IOException {
		Scanner input = new Scanner(System.in);
		try {

			System.out.print(">");
			String command = input.next();

			if (command.strip().equalsIgnoreCase("shutdown")) {
				try {
					server.shutdown();
					serverThread.interrupt();
				} finally {
					System.exit(0);
				}

			} else if (command.strip().equalsIgnoreCase("timeout")) {
				System.out.format(">number (-1 to remove the timeout): ", Server.MAXIMUM_SPAWN_RATE);

				if (input.hasNextInt()) {
					int count = input.nextInt();
					if (count == -1 || count > 0) {
						System.out.println(count != -1 ? "info: timeout set to: " + count + " minute(s)"
								: "info: timeout removed");
						server.updateTimeout(count * 60000);
						cliServerManagement(server, serverThread);

					} else {
						System.out.println("error: invalid number " + count);
						cliServerManagement(server, serverThread);
					}

				} else {
					System.out.println("error: invalid number " + input.next());
					cliServerManagement(server, serverThread);
				}

			} else if (command.strip().equalsIgnoreCase("spawn")) {
				System.out.format(">number (max. %s): ", Server.MAXIMUM_SPAWN_RATE);

				if (input.hasNextInt()) {
					int count = input.nextInt();
					if (count <= Server.MAXIMUM_SPAWN_RATE && count > 0) {
						System.out.println("info: spawn rate set to: " + count);
						server.updateSpawnRate(count);
						cliServerManagement(server, serverThread);

					} else {
						System.out.println("error: invalid number " + count);
						cliServerManagement(server, serverThread);
					}

				} else {
					System.out.println("error: invalid number " + input.next());
					cliServerManagement(server, serverThread);
				}

			} else if (command.strip().equalsIgnoreCase("capacity")) {
				System.out.format(">number (max. %s): ", Server.MAXIMUM_CAPACITY);

				if (input.hasNextInt()) {
					int count = input.nextInt();

					if (count <= Server.MAXIMUM_CAPACITY && count > 0) {
						System.out.println("info: capacity set to: " + count);
						server.updateCapacity(count);
						cliServerManagement(server, serverThread);

					} else {
						System.out.println("error: invalid number " + count);
						cliServerManagement(server, serverThread);
					}

				} else {
					System.out.println("error: invalid number " + input.next());
					cliServerManagement(server, serverThread);
				}

			} else if (command.strip().equalsIgnoreCase("reload")) {
				System.out.println("info: reloading...");
				server.reloadSaveFile();
				cliServerManagement(server, serverThread);

			} else if (command.strip().equalsIgnoreCase("help")) {
				commandsList();
				cliServerManagement(server, serverThread);

			} else {
				System.out.println("error: unknown command " + command);
				commandsList();
				cliServerManagement(server, serverThread);
			}

		} finally {
			input.close();
		}

	}

}
