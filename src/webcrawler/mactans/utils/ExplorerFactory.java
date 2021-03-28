package webcrawler.mactans.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import webcrawler.mactans.explorer.Explorer;

/**
 * Factory for making new explorers.
 *
 */
public class ExplorerFactory {

	/**
	 * Creates new explorers.
	 * 
	 * @param port   port to which explorers connect to
	 * @param number the number of explorers to be constructed
	 * @param id     client ID to whom the work is to be returned
	 * @return a list of explorers
	 * @see Explorer
	 */
	public static final List<Explorer> makeExplorers(int port, int number, UUID id) {
		List<Explorer> explorers = new ArrayList<Explorer>();

		for (int i = 0; i < number; i++) {
			explorers.add(new Explorer(port, id));
		}
		return explorers;
	}

	/**
	 * Starts the threads for the given explorers.
	 * 
	 * @param explorers threads to be started
	 * @see Explorer
	 */
	public static final void startExplorers(List<Explorer> explorers) {
		int number = explorers.size();

		if (number == 1) {
			explorers.get(0).start();

		} else if (number > 1) {
			explorers.get(0).start();

			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				System.out.println(e.getMessage());
			}

			for (int i = 1; i < explorers.size(); i++) {
				explorers.get(i).start();
			}
		}
	}
}
