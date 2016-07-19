package freedots;

public class Driver {
	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Usage: <path-to-xml>+");
			return;
		}
		// int i = 0;
		for (String arg : args) {
			// System.out.print("" + ++i + "/" + args.length + "\t" + arg);
			// System.out.println("Transcribing " + arg);
			// String[] params = { "-nw", "-p", arg }; // play
			String[] params = { "-nw", arg }; // don't play
			Main.main(params);
		}
	}
}
