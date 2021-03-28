package webcrawler.mactans.launcher;

import java.io.IOException;

import webcrawler.mactans.server.Server;
import webcrawler.mactans.utils.LaunchOptions;

/**
 * Launcher class for the project.
 *
 */
public class Launcher {

	public static void main(String[] args) {
		int explorersPort = 0, clientsPort = 0;

		Server server = new Server(explorersPort, clientsPort);

		Thread serverThread = new Thread(server);
		serverThread.start();
		
		LaunchOptions.greetings();

		try {
			LaunchOptions.cliServerManagement(server, serverThread);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
