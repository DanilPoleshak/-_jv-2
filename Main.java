import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Мышь в лабиринте");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Создаем окно. Передаем null, так как лабиринт сгенерируется внутри сам при старте
            MazeWindow mazePanel = new MazeWindow();
            frame.add(mazePanel);

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }
}
