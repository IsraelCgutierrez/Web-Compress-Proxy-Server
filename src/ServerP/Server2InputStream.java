package ServerP;
import java.io.IOException;

public interface Server2InputStream
{
  /** reads the data */
  public int read_f(byte[] b) throws IOException;
}
