import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.Vector;

public class FarmersMarketApp {
    private static Connection conn;
    private static int currentUserId;
    private static String currentRole;
    private static int associatedId;

    public static void main(String[] args) {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
            String url = "jdbc:oracle:thin:@localhost:1521:XE";
            String username = "system";
            String password = "12345678";
            conn = DriverManager.getConnection(url, username, password);
            conn.setAutoCommit(false);
            createLoginWindow();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createLoginWindow() {
        JFrame loginFrame = new JFrame("Farmers Market - Login");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setSize(400, 250);
        loginFrame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel usernameLabel = new JLabel("Username:");
        JTextField usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("Password:");
        JPasswordField passwordField = new JPasswordField();
        JLabel roleLabel = new JLabel("Role:");
        JComboBox<String> roleCombo = new JComboBox<>(new String[]{"Farmer", "Customer"});
        JButton loginButton = new JButton("Login");

        panel.add(usernameLabel);
        panel.add(usernameField);
        panel.add(passwordLabel);
        panel.add(passwordField);
        panel.add(roleLabel);
        panel.add(roleCombo);
        panel.add(new JLabel());
        panel.add(loginButton);

        loginButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            String role = (String) roleCombo.getSelectedItem();

            try {
                String query = "SELECT UserID, Role, AssociatedID FROM USER3 WHERE Username = ? AND Password = ? AND Role = ?";
                PreparedStatement pstmt = conn.prepareStatement(query);
                pstmt.setString(1, username);
                pstmt.setString(2, password);
                pstmt.setString(3, role);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    currentUserId = rs.getInt("UserID");
                    currentRole = rs.getString("Role");
                    associatedId = rs.getInt("AssociatedID");
                    loginFrame.dispose();
                    if (role.equals("Farmer")) {
                        createFarmerDashboard();
                    } else {
                        createCustomerDashboard();
                    }
                } else {
                    JOptionPane.showMessageDialog(loginFrame, "Invalid credentials or role");
                }
                rs.close();
                pstmt.close();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(loginFrame, "Error: " + ex.getMessage());
            }
        });

        loginFrame.add(panel);
        loginFrame.setVisible(true);
    }

    private static void createFarmerDashboard() {
        JFrame farmerFrame = new JFrame("Farmer Dashboard");
        farmerFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        farmerFrame.setSize(500, 350);
        farmerFrame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel cropTypeLabel = new JLabel("Crop Type:");
        JTextField cropTypeField = new JTextField();
        JLabel quantityLabel = new JLabel("Quantity (kg):");
        JTextField quantityField = new JTextField();
        JLabel priceLabel = new JLabel("Price per kg:");
        JTextField priceField = new JTextField();
        JButton addButton = new JButton("Add Crop");
        JButton logoutButton = new JButton("Logout");

        panel.add(cropTypeLabel);
        panel.add(cropTypeField);
        panel.add(quantityLabel);
        panel.add(quantityField);
        panel.add(priceLabel);
        panel.add(priceField);
        panel.add(new JLabel());
        panel.add(addButton);
        panel.add(new JLabel());
        panel.add(logoutButton);

        addButton.addActionListener(e -> {
            String cropType = cropTypeField.getText();
            String quantityStr = quantityField.getText();
            String priceStr = priceField.getText();

            try {
                int quantity = Integer.parseInt(quantityStr);
                double price = Double.parseDouble(priceStr);

                if (cropType.isEmpty() || quantity <= 0 || price <= 0) {
                    JOptionPane.showMessageDialog(farmerFrame, "Please fill all fields with valid data");
                    return;
                }

                String query = "INSERT INTO AvailableCrops (CropID, FarmerID, CropType, Quantity, Price) " +
                        "VALUES (crop_seq.NEXTVAL, ?, ?, ?, ?)";
                PreparedStatement pstmt = conn.prepareStatement(query);
                pstmt.setInt(1, associatedId);
                pstmt.setString(2, cropType);
                pstmt.setInt(3, quantity);
                pstmt.setDouble(4, price);
                pstmt.executeUpdate();
                conn.commit();

                JOptionPane.showMessageDialog(farmerFrame, "Crop added successfully!");
                cropTypeField.setText("");
                quantityField.setText("");
                priceField.setText("");
                pstmt.close();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(farmerFrame, "Quantity and Price must be valid numbers");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(farmerFrame, "Error: " + ex.getMessage());
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
        });

        logoutButton.addActionListener(e -> {
            farmerFrame.dispose();
            createLoginWindow();
        });

        farmerFrame.add(panel);
        farmerFrame.setVisible(true);
    }

    private static void createCustomerDashboard() {
        JFrame customerFrame = new JFrame("Customer Dashboard");
        customerFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        customerFrame.setSize(600, 450);
        customerFrame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTable cropsTable = new JTable();
        JScrollPane scrollPane = new JScrollPane(cropsTable);
        JButton signContractButton = new JButton("Sign Contract for Selected Crop");
        JButton logoutButton = new JButton("Logout");

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(signContractButton);
        bottomPanel.add(logoutButton);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        try {
            String query = "SELECT ac.CropID, f.Name AS FarmerName, ac.CropType, ac.Quantity, ac.Price " +
                    "FROM AvailableCrops ac JOIN Farmers f ON ac.FarmerID = f.FarmerID";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);

            Vector<String> columnNames = new Vector<>();
            columnNames.add("Crop ID");
            columnNames.add("Farmer");
            columnNames.add("Crop Type");
            columnNames.add("Quantity (kg)");
            columnNames.add("Price per kg");

            Vector<Vector<Object>> data = new Vector<>();
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("CropID"));
                row.add(rs.getString("FarmerName"));
                row.add(rs.getString("CropType"));
                row.add(rs.getInt("Quantity"));
                row.add(rs.getDouble("Price"));
                data.add(row);
            }

            cropsTable.setModel(new javax.swing.table.DefaultTableModel(data, columnNames));
            rs.close();
            stmt.close();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(customerFrame, "Error loading crops: " + ex.getMessage());
        }

        signContractButton.addActionListener(e -> {
            int row = cropsTable.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(customerFrame, "Please select a crop to sign contract");
                return;
            }

            int cropId = (Integer) cropsTable.getValueAt(row, 0);
            String cropType = (String) cropsTable.getValueAt(row, 2);
            int availableQuantity = (Integer) cropsTable.getValueAt(row, 3);
            double pricePerKg = (Double) cropsTable.getValueAt(row, 4);

            String quantityStr = JOptionPane.showInputDialog(customerFrame,
                    "Enter quantity to purchase (max " + availableQuantity + " kg):");
            try {
                int quantity = Integer.parseInt(quantityStr);
                if (quantity <= 0 || quantity > availableQuantity) {
                    JOptionPane.showMessageDialog(customerFrame, "Invalid quantity");
                    return;
                }

                double totalPrice = quantity * pricePerKg;
                String contractQuery = "INSERT INTO Contracts (ContractID, FarmerID, BuyerID, CropDetails, Quantity, Price) " +
                        "VALUES (contract_seq.NEXTVAL, (SELECT FarmerID FROM AvailableCrops WHERE CropID = ?), ?, ?, ?, ?)";
                PreparedStatement pstmt = conn.prepareStatement(contractQuery);
                pstmt.setInt(1, cropId);
                pstmt.setInt(2, associatedId);
                pstmt.setString(3, cropType + " Supply");
                pstmt.setInt(4, quantity);
                pstmt.setDouble(5, totalPrice);
                pstmt.executeUpdate();

                String updateQuery = "UPDATE AvailableCrops SET Quantity = Quantity - ? WHERE CropID = ?";
                pstmt = conn.prepareStatement(updateQuery);
                pstmt.setInt(1, quantity);
                pstmt.setInt(2, cropId);
                pstmt.executeUpdate();

                String checkQuery = "DELETE FROM AvailableCrops WHERE CropID = ? AND Quantity = 0";
                pstmt = conn.prepareStatement(checkQuery);
                pstmt.setInt(1, cropId);
                pstmt.executeUpdate();

                conn.commit();
                JOptionPane.showMessageDialog(customerFrame, "Contract signed successfully!");
                customerFrame.dispose();
                createCustomerDashboard();
                pstmt.close();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(customerFrame, "Invalid quantity");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(customerFrame, "Error: " + ex.getMessage());
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
        });

        logoutButton.addActionListener(e -> {
            customerFrame.dispose();
            createLoginWindow();
        });

        customerFrame.add(panel);
        customerFrame.setVisible(true);
    }

    @Override
    protected void finalize() throws Throwable {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
        super.finalize();
}
}