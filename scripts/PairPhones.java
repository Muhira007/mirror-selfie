import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;

/** USB-only test helper: decode the displayed QR in memory; media remains on the LAN. */
class PairPhones {
    public static void main(String[] args) throws Exception {
        Process capture = new ProcessBuilder(args[0], "-s", args[1], "exec-out", "screencap", "-p").start();
        BufferedImage image = ImageIO.read(capture.getInputStream());
        if (capture.waitFor() != 0 || image == null) throw new IOException("Cannot capture sender QR");
        int w = image.getWidth(), h = image.getHeight();
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, pixels)));
        Result result = new MultiFormatReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, true));
        String code = result.getText();
        if (!code.startsWith("mirror1:") || code.length() > 2048) throw new IOException("Unexpected QR");
        Process join = new ProcessBuilder(args[0], "-s", args[2], "shell", "am", "start", "-n", "id.mirrorselfie/.MainActivity", "-f", "0x24000000", "--es", "joinCode", code).start();
        join.getInputStream().transferTo(System.out);
        join.getErrorStream().transferTo(System.err);
        if (join.waitFor() != 0) throw new IOException("Cannot start monitor");
    }
}
