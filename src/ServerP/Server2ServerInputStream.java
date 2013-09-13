package ServerP;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

public class Server2ServerInputStream extends BufferedInputStream implements Server2InputStream
{
	private Server2HTTPSession connection;

	public Server2ServerInputStream(Server2Server server,
			Server2HTTPSession connection, InputStream a, boolean filter) {
		super(a);
		this.connection = connection;
	}

	public int read_f(byte[] b) throws IOException {
		return read(b);
	}
}

