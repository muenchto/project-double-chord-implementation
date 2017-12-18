import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;

/**
 * Client class that offers the interface by which users can do search by
 * querying a valid chord node.
 *
 * @author Chuan Xia
 *
 */

public class Client {

	private static InetSocketAddress localAddress;
	private static Helper helper;

	public static void main(String[] args) {

		helper = new Helper();

		// valid args
		if (args.length == 2) {

			// try to parse socket address from args, if fail, exit
			localAddress = Helper.createSocketAddress(args[0] + ":" + args[1]);
			if (localAddress == null) {
				System.out.println("Cannot find address you are trying to contact. Now exit.");
				System.exit(0);
				;
			}

			// successfully constructed socket address of the node we are
			// trying to contact, check if it's alive
			String response = Helper.sendRequest(localAddress, "KEEP");

			// if it's dead, exit
			if (response == null || !response.equals("ALIVE")) {
				System.out.println("\nCannot find node you are trying to contact. Now exit.\n");
				System.exit(0);
			}

			// it's alive, print connection info
			System.out.println("Connection to node " + localAddress.getAddress().toString() + ", port "
					+ localAddress.getPort() + ", position " + Helper.hexIdAndPosition(localAddress) + ".");

			// check if system is stable
			boolean pred = false;
			boolean succ = false;
			InetSocketAddress pred_addr = Helper.requestAddress(localAddress, "YOURPRE");
			InetSocketAddress succ_addr = Helper.requestAddress(localAddress, "YOURSUCC");
			if (pred_addr == null || succ_addr == null) {
				System.out.println("The node your are contacting is disconnected. Now exit.");
				System.exit(0);
			}
			if (pred_addr.equals(localAddress))
				pred = true;
			if (succ_addr.equals(localAddress))
				succ = true;

			// we suppose the system is stable if (1) this node has both valid
			// predecessor and successor or (2) none of them
			while (pred ^ succ) {
				System.out.println("Waiting for the system to be stable...");
				pred_addr = Helper.requestAddress(localAddress, "YOURPRE");
				succ_addr = Helper.requestAddress(localAddress, "YOURSUCC");
				if (pred_addr == null || succ_addr == null) {
					System.out.println("The node your are contacting is disconnected. Now exit.");
					System.exit(0);
				}
				if (pred_addr.equals(localAddress))
					pred = true;
				else
					pred = false;
				if (succ_addr.equals(localAddress))
					succ = true;
				else
					succ = false;
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
				}

			}

			// begin to take user input
			Scanner userinput = new Scanner(System.in);
			Scanner userinput2 = new Scanner(System.in);
			Scanner userinput3 = new Scanner(System.in);
			while (true) {

				String command = null;
				int command1 = 0;

				while (command1 <= 0 || command1 >= 4) {
					System.out.println("What you want to do ? (insert the number 1, 2 or 3)");
					System.out.println("1 -> Insert Domain and IP");
					System.out.println("2 -> ResolveDNS");
					System.out.println("3 -> QUIT");
					command1 = userinput.nextInt();
				}

				// quit
				if (command1 == 3) {
					System.exit(0);
				}

				// PUT
				else if (command1 == 1) {
					System.out.println("Please write your query for insert Domain and IP ( put <domain> <IP>) ");

					command = userinput2.nextLine();

					String[] tok = command.split(" ");
					
					while(tok.length != 3) {
						System.out.println("Error with input, write correctly, like in example.");
						System.out.println("Please write your query for insert Domain and IP ( put <domain> <IP>) ");
						command = userinput2.nextLine();
						tok = command.split(" ");
					}

					String ippDomain = getIPport(tok[1], 0);

					String ippIp = getIPport(tok[2], 0);

					String secondDomain = getIPport(tok[1], 1);

					String secondIp = getIPport(tok[2], 1);

					String[] domainTok = ippDomain.split(":");
					String[] ipTok = ippIp.split(":");
					String[] secondDomTok = secondDomain.split(":");
					String[] secondIpTok = secondIp.split(":");

					try {
						// Socket for node in First Ring
						Socket ss1 = new Socket(domainTok[0], Integer.parseInt(domainTok[1]) + 2000);
						Socket ss2 = new Socket(ipTok[0], Integer.parseInt(ipTok[1]) + 2000);

						// Socket for node in Second Ring
						Socket ss3 = new Socket(secondDomTok[0], Integer.parseInt(secondDomTok[1]) + 2000);
						Socket ss4 = new Socket(secondIpTok[0], Integer.parseInt(secondIpTok[1]) + 2000);

						//
						ObjectOutputStream oos1 = new ObjectOutputStream(ss1.getOutputStream());
						oos1.flush();
						ObjectOutputStream oos2 = new ObjectOutputStream(ss2.getOutputStream());
						oos2.flush();

						ObjectOutputStream oos3 = new ObjectOutputStream(ss3.getOutputStream());
						oos3.flush();

						ObjectOutputStream oos4 = new ObjectOutputStream(ss4.getOutputStream());
						oos4.flush();

						// Send to node in First Ring
						oos1.writeObject("putd");
						oos1.writeObject(tok[1]);
						oos1.writeObject(tok[2]);
						oos1.flush();

						// Send to node in First Ring
						oos2.writeObject("putip");
						oos2.writeObject(tok[1]);
						oos2.writeObject(tok[2]);
						oos2.flush();

						// Send to node in Second Ring
						oos3.writeObject("putd");
						oos3.writeObject(tok[1]);
						oos3.writeObject(tok[2]);
						oos3.flush();

						// Send to node in Second Ring
						oos4.writeObject("putip");
						oos4.writeObject(tok[1]);
						oos4.writeObject(tok[2]);
						oos4.flush();

						System.out.println("You putted this: put domain: " + tok[1] + " IP: " + tok[2]);

						oos1.close();
						oos2.close();
						oos3.close();
						oos4.close();
						ss1.close();
						ss2.close();
						ss3.close();
						ss4.close();
					} catch (NumberFormatException | IOException e) {
						e.printStackTrace();
					}

					// GET
				} else if (command1 == 2) {

					String get = null;
					System.out.println("Which Resolve you want? (NORMAL or REVERSE)");
					get = userinput2.nextLine();

					if (get.toUpperCase().equals("NORMAL")) {
						System.out.println("Error with input, write correctly, like in example.");
						System.out.println(" Insert get <Domain>");
						command = userinput2.nextLine();

						String[] tok = command.split(" ");

						while(tok.length != 2) {
							System.out.println(" Insert get <Domain>");
							command = userinput2.nextLine();
							tok = command.split(" ");
						}

						String ipp = getIPport(tok[1]);
						String[] ipptk = ipp.split(":");

						try {
							Socket ss = new Socket(ipptk[0], Integer.parseInt(ipptk[1]) + 2000);

							ObjectOutputStream oos = new ObjectOutputStream(ss.getOutputStream());
							ObjectInputStream ios = new ObjectInputStream(ss.getInputStream());
							oos.flush();
							oos.writeObject("getd");
							oos.writeObject(tok[1]);
							oos.flush();
							String ret = (String) ios.readObject();

							if (ret != null) {
								System.out.println("The IP of Domain: " + tok[1] + " is: " + ret);
							} else {
                                for (int i = 0; i < 2; i++) {

                                    System.out.println("Could not find " + tok[1] + ". Retry in RING" + i);
                                    ipp = getIPport(tok[1]);
                                    ipptk = ipp.split(":");
                                    ss = new Socket(ipptk[0], Integer.parseInt(ipptk[1]) + 2000);

                                    oos = new ObjectOutputStream(ss.getOutputStream());
                                    ios = new ObjectInputStream(ss.getInputStream());
                                    oos.flush();
                                    oos.writeObject("getd");
                                    oos.writeObject(tok[1]);
                                    oos.flush();
                                    ret = (String) ios.readObject();
                                    if (ret != null) {
                                        System.out.println("RING" + i +" The IP of Domain: " + tok[1] + " is: " + ret);
                                        break;
                                    }
                                }
							}

							ios.close();
							oos.close();
							ss.close();

						} catch (NumberFormatException | IOException | ClassNotFoundException e) {
							e.printStackTrace();
						}
					} else if (get.toUpperCase().equals("REVERSE")) {
						System.out.println(" Insert get <IP>");
						command = userinput3.nextLine();

						String[] tok = command.split(" ");

						while(tok.length != 2) {
							System.out.println("Error with input, write correctly, like in example.");
							System.out.println(" Insert get <IP>");
							command = userinput3.nextLine();
							tok = command.split(" ");
						}
						String ipp = getIPport(tok[1]);
						String[] ipptk = ipp.split(":");

						try {
							Socket ss = new Socket(ipptk[0], Integer.parseInt(ipptk[1]) + 2000);

							ObjectOutputStream oos = new ObjectOutputStream(ss.getOutputStream());
							ObjectInputStream ios = new ObjectInputStream(ss.getInputStream());
							oos.flush();

							// Write to Node
							oos.writeObject("getip");
							oos.writeObject(tok[1]);
							oos.flush();

							// Receive from Node
							String ret = (String) ios.readObject();
							if (!ret.equals(null)) {
								System.out.println("The Domain of IP: " + tok[1] + " is: " + ret);
							} else {
								System.out.println("Some error occurred, may be u didn't put this IP on Network");
							}

							ios.close();
							oos.close();
							ss.close();

						} catch (NumberFormatException | IOException | ClassNotFoundException e) {
							e.printStackTrace();
						}
					}else {
						System.out.println("Sorry, Try again.");
					}

				}
			}
		} else {
			System.out.println("\nInvalid input. Now exit.\n");
		}
	}

	private static String getIPport(String command) {
		int ring_nr = -1;
		return getIPport(command, ring_nr);
	}

	private static String getIPport(String command, int ring_nr) {
		long hash = Helper.hashString(command);

		System.out.println("\nHash value is " + Long.toHexString(hash));

		InetSocketAddress result;
		if (ring_nr == -1) {
			// dont care about rings, find the closest server for hash
			result = Helper.requestAddress(localAddress, "FINDSUCC_" + hash);
		} else {
			// care about rings, find the responsible server in specified ring
			result = Helper.requestAddress(localAddress, "FINDSUCC_" + hash, ring_nr);
		}

		// if fail to send request, local node is disconnected, exit
		if (result == null) {
			System.out.println("The node you are contacting is disconnected. Now exit.");
			System.exit(0);
		}

		return result.getAddress().toString().substring(1, result.getAddress().toString().length()) + ":"
				+ result.getPort();
	}
}
