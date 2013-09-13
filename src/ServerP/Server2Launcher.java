package ServerP;
public class Server2Launcher {

  static Server2Server server;

  public static void main(String[] args)
  {
		server = new Server2Server(true);
    	if (server.fatalError) {
    		System.out.println("Error: " +  server.getErrorMessage());
		}
    	else {
    		new Thread(server).start();
    	   	System.out.println("Running on port " + server.port);
    	}
  }
}