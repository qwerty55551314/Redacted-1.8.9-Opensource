package redacted.client.util.gui;

public class GUIUtil {
    public static boolean mouseOver(double posX, double posY, double width, double height, double mouseX, double mouseY) {
        return mouseX >= posX && mouseX <= posX + width && mouseY >= posY && mouseY <= posY + height;
    }
}
