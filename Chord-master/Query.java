import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;

/**
 * Query class that offers the interface by which users can do 
 * search by querying a valid chord node.
 * @author Chuan Xia
 *
 */

public class Query {

	private static InetSocketAddress localAddress;
	private static Helper helper;

	public static void main (String[] args) {

		helper = new Helper();

		// valid args
		if (args.length == 2) {

			// try to parse socket address from args, if fail, exit
			localAddress = Helper.createSocketAddress(args[0]+":"+args[1]);
			if (localAddress == null) {
				System.out.println("Cannot find address you are trying to contact. Now exit.");
				System.exit(0);;	
			}

			// successfully constructed socket address of the node we are 
			// trying to contact, check if it's alive
			String response = Helper.sendRequest(localAddress, "KEEP");

			// if it's dead, exit
			if (response == null || !response.equals("ALIVE"))  {
				System.out.println("\nCannot find node you are trying to contact. Now exit.\n");
				System.exit(0);
			}

			// it's alive, print connection info
			System.out.println("Connection to node "+localAddress.getAddress().toString()+", port "+localAddress.getPort()+", position "+Helper.hexIdAndPosition(localAddress)+".");

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
			while (pred^succ) {
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
			while(true) {

				String command = null;
				int command1 = 0;


				
				while(command1 <= 0 || command1 >=4 ) {
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
				
				//PUT
				else if(command1 == 1) {
					System.out.println("Please write your query for insert Domain and IP ( put <domain> <IP>) ");

					command = userinput2.nextLine();
					System.out.println("eheh -> "+command);
					String[] tok = command.split(" ");
					System.out.println("to k " +tok[0]);
					String ippDomain = getIPport(tok[1]);

					String ippIp = getIPport(tok[2]);

					String[] domainTok = ippDomain.split(":");
					String[] ipTok = ippIp.split(":");

					try {
						Socket ss1 = new Socket(domainTok[0], Integer.parseInt(domainTok[1])+2000);
						Socket ss2 = new Socket(ipTok[0], Integer.parseInt(ipTok[1])+2000);
						//ObjectInputStream ois = new ObjectInputStream(ss.getInputStream());
					    ObjectOutputStream oos1 = new ObjectOutputStream(ss1.getOutputStream());
					    oos1.flush();

					    ObjectOutputStream oos2 = new ObjectOutputStream(ss2.getOutputStream());
					    oos2.flush();

					    oos1.writeObject("putd");
					    oos1.writeObject(tok[1]);
					    oos1.writeObject(tok[2]);
					    oos1.flush();

					    oos2.writeObject("putip");
					    oos2.writeObject(tok[1]);
					    oos2.writeObject(tok[2]);
					    oos2.flush();


					    oos1.close();
					    oos2.close();
					    ss1.close();
					    ss2.close();

					} catch (NumberFormatException | IOException e) {
						e.printStackTrace();
					}

				//GET
				}else if(command1 == 2) {

					String get = null;
					System.out.println("Which Resolve you want? (NORMAL or REVERSE)");
					get = userinput2.nextLine();
//					while(!get.equals("NORMAL") || !get.equals("REVERSE")) {
//						System.out.println("Which Resolve you want? (NORMAL or REVERSE)");
//						get = userinput2.nextLine();
//					}
					if(get.equals("NORMAL")) {
                        System.out.println(" Insert get <Domain>");
                        command = userinput2.nextLine();

						String[] tok = command.split(" ");
						String ipp = getIPport(tok[1]);
						String[] ipptk = ipp.split(":");

						try {
							Socket ss = new Socket(ipptk[0], Integer.parseInt(ipptk[1])+2000);

				    			ObjectOutputStream oos = new ObjectOutputStream(ss.getOutputStream());
							ObjectInputStream ios = new ObjectInputStream(ss.getInputStream());
					    		oos.flush();
					    		oos.writeObject("getd");
					    		oos.writeObject(tok[1]);
					    		oos.flush();
					    		String ret = (String) ios.readObject();

					    		System.out.println(ret);

					    		ios.close();
					    		oos.close();
					    		ss.close();

						} catch (NumberFormatException | IOException | ClassNotFoundException e) {
							e.printStackTrace();
						}
					}else if(get.equals("REVERSE")) {
                        System.out.println(" Insert get <IP>");
						command = userinput3.nextLine();

						String[] tok = command.split(" ");
						String ipp = getIPport(tok[1]);
						String[] ipptk = ipp.split(":");

						try {
							Socket ss = new Socket(ipptk[0], Integer.parseInt(ipptk[1])+2000);

				    			ObjectOutputStream oos = new ObjectOutputStream(ss.getOutputStream());
							ObjectInputStream ios = new ObjectInputStream(ss.getInputStream());
					    		oos.flush();
					    		oos.writeObject("getip");
					    		oos.writeObject(tok[1]);
					    		oos.flush();
					    		String ret = (String) ios.readObject();

					    		System.out.println(ret);

					    		ios.close();
					    		oos.close();
					    		ss.close();

						} catch (NumberFormatException | IOException | ClassNotFoundException e) {
							e.printStackTrace();
						}
					}

				}
				// search
//				else if (command.length() > 0){
//
//				}
			}
		}
		else {
			System.out.println("\nInvalid input. Now exit.\n");
		}
	}

	private static String getIPport(String command) {
		int ring_nr = -1;
		return getIPport(command, ring_nr);
	}

	private static String getIPport(String command, int ring_nr){
		long hash = Helper.hashString(command);

        System.out.println("\nHash value is "+Long.toHexString(hash));

		InetSocketAddress result;
		if (ring_nr == -1) {
			// dont care about rings, find the closest server for hash
			result = Helper.requestAddress(localAddress, "FINDSUCC_" + hash);
		} else {
			//care about rings, find the responsible server in specified ring
			result = Helper.requestAddress(localAddress, "FINDSUCC_" + hash, ring_nr);
		}


		// if fail to send request, local node is disconnected, exit
		if (result == null) {
			System.out.println("The node you are contacting is disconnected. Now exit.");
			System.exit(0);
		}

		return result.getAddress().toString().substring(1, result.getAddress().toString().length()) + ":" + result.getPort();
	}
}
