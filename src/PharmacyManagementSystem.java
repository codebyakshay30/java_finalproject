import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/** Pharmacy Billing & Stock Management System (Swing GUI) - data is saved to pharmacy_data.txt */
public class PharmacyManagementSystem {

    // ---------- Data (parallel arrays) ----------
    static final int MAX = 100, MAX_BILLS = 500, LOW_STOCK_LIMIT = 10;
    static final String DATA_FILE = "pharmacy_data.txt";

    static String[] names = new String[MAX];
    static double[] prices = new double[MAX];
    static int[] stock = new int[MAX];
    static int count = 0;

    static int[] cartIdx = new int[MAX];
    static int[] cartQty = new int[MAX];
    static int cartCount = 0;

    static String[] histDate = new String[MAX_BILLS];
    static String[] histCustomer = new String[MAX_BILLS];
    static String[] histItems = new String[MAX_BILLS];
    static double[] histTotal = new double[MAX_BILLS];
    static int histCount = 0;

    // ---------- GUI ----------
    static final Color NAVY = new Color(22, 40, 84);
    static final Color TEAL = new Color(0, 150, 136);
    static final Color RED = new Color(198, 40, 40);
    static final Color GREY = new Color(96, 110, 125);

    static JFrame frame;
    static CardLayout cards = new CardLayout();
    static JPanel content = new JPanel(cards);

    static DefaultTableModel stockModel = model("ID", "Name", "Price (Rs.)", "Stock", "Status");
    static DefaultTableModel cartModel = model("Medicine", "Qty", "Price (Rs.)", "Amount (Rs.)");
    static DefaultTableModel searchModel = model("ID", "Name", "Price (Rs.)", "Stock");
    static DefaultTableModel restockModel = model("ID", "Name", "Stock", "Status");

    static JTextField regName, regPrice, regQty, custField, quickField, searchField;
    static JComboBox<String> medBox;
    static JSpinner qtySpinner;
    static JLabel totalLabel;
    static JTextArea historyArea;
    static JTable cartTable, restockTable;

    public static void main(String[] args) {
        if (!loadData()) {
            preloadMedicines();
            saveData();
        }
        SwingUtilities.invokeLater(PharmacyManagementSystem::buildGui);
    }

    // ================= File storage =================
    static String clean(String s) {
        return s.replace("\t", " ").replace("\n", " ").replace("\r", " ");
    }

    static void saveData() {
        try (PrintWriter out = new PrintWriter(new FileWriter(DATA_FILE))) {
            for (int i = 0; i < count; i++) {
                out.println("M\t" + clean(names[i]) + "\t" + prices[i] + "\t" + stock[i]);
            }
            for (int i = 0; i < histCount; i++) {
                out.println("H\t" + histDate[i] + "\t" + clean(histCustomer[i]) + "\t" + clean(histItems[i]) + "\t" + histTotal[i]);
            }
        } catch (IOException e) {
            error("Could not save data: " + e.getMessage());
        }
    }

    static boolean loadData() {
        File f = new File(DATA_FILE);
        if (!f.exists()) {
            return false;
        }
        try (Scanner sc = new Scanner(f)) {
            while (sc.hasNextLine()) {
                String[] p = sc.nextLine().split("\t");
                if (p[0].equals("M") && p.length == 4 && count < MAX) {
                    addMedicine(p[1], Double.parseDouble(p[2]), Integer.parseInt(p[3]));
                } else if (p[0].equals("H") && p.length == 5 && histCount < MAX_BILLS) {
                    histDate[histCount] = p[1];
                    histCustomer[histCount] = p[2];
                    histItems[histCount] = p[3];
                    histTotal[histCount] = Double.parseDouble(p[4]);
                    histCount++;
                }
            }
        } catch (Exception e) {
            System.out.println("Could not read data file: " + e.getMessage());
        }
        return count > 0;
    }

    // ================= Medicine registration =================
    static void preloadMedicines() {
        addMedicine("Paracetamol", 20.00, 50);
        addMedicine("Amoxicillin", 85.50, 30);
        addMedicine("Cetirizine", 12.00, 8);
        addMedicine("Ibuprofen", 45.00, 40);
    }

    static void addMedicine(String name, double price, int qty) {
        names[count] = name;
        prices[count] = price;
        stock[count] = qty;
        count++;
    }

    static void registerMedicine() {
        String name = regName.getText().trim();
        if (name.isEmpty()) {
            error("Please enter the medicine name.");
            return;
        }
        double price;
        int qty;
        try {
            price = Double.parseDouble(regPrice.getText().trim());
            qty = Integer.parseInt(regQty.getText().trim());
        } catch (NumberFormatException e) {
            error("Price must be a number and quantity must be a whole number.");
            return;
        }
        if (price <= 0 || qty <= 0) {
            error("Price and quantity must be greater than 0.");
            return;
        }

        int existing = findByName(name);
        if (existing != -1) {
            stock[existing] += qty;
            info("'" + names[existing] + "' already exists.\nStock increased. New stock = " + stock[existing]);
        } else if (count >= MAX) {
            error("Storage full! Cannot add more medicines.");
            return;
        } else {
            addMedicine(name, price, qty);
            info("Medicine registered successfully with ID " + count + ".");
        }
        regName.setText("");
        regPrice.setText("");
        regQty.setText("");
        refreshAll();
        saveData();
    }

    // ================= Billing =================
    static String checkStock(int idx, int qty, int alreadyInCart) {
        int available = stock[idx] - alreadyInCart;
        if (stock[idx] == 0) {
            return names[idx] + " is out of stock.";
        } else if (qty > available) {
            return "Insufficient stock for " + names[idx] + ": only " + available + " more can be added.";
        }
        return null;
    }

    static void addSelected() {
        int idx = medBox.getSelectedIndex();
        if (idx < 0) {
            error("No medicine available.");
            return;
        }
        int qty = (Integer) qtySpinner.getValue();
        String err = checkStock(idx, qty, quantityInCart(idx));
        if (err != null) {
            error(err);
            return;
        }
        addToCart(idx, qty);
        refreshCart();
    }

    // Quick add: "1:5, 2:3, 3:4" -> ProductID:Quantity pairs, read with a Scanner
    static void quickAdd() {
        String text = quickField.getText().trim();
        if (text.isEmpty()) {
            error("Type items like  1:5, 2:3, 3:4  (ProductID:Quantity).");
            return;
        }
        StringBuilder errors = new StringBuilder();
        Scanner sc = new Scanner(text.replace(",", " ").replace(":", " "));
        while (sc.hasNext()) {
            if (!sc.hasNextInt()) {
                errors.append("Invalid entry: ").append(sc.next()).append("\n");
                continue;
            }
            int id = sc.nextInt();
            if (!sc.hasNextInt()) {
                errors.append("Missing quantity for product ").append(id).append("\n");
                break;
            }
            int qty = sc.nextInt();
            if (id < 1 || id > count) {
                errors.append("Product ID ").append(id).append(" does not exist.\n");
            } else if (qty < 1) {
                errors.append("Quantity for product ").append(id).append(" must be at least 1.\n");
            } else {
                String err = checkStock(id - 1, qty, quantityInCart(id - 1));
                if (err != null) {
                    errors.append(err).append("\n");
                } else {
                    addToCart(id - 1, qty);
                }
            }
        }
        sc.close();
        quickField.setText("");
        refreshCart();
        if (errors.length() > 0) {
            error(errors.toString());
        }
    }

    static void addToCart(int idx, int qty) {
        int pos = positionInCart(idx);
        if (pos != -1) {
            cartQty[pos] += qty;
        } else {
            cartIdx[cartCount] = idx;
            cartQty[cartCount] = qty;
            cartCount++;
        }
    }

    static int positionInCart(int idx) {
        for (int i = 0; i < cartCount; i++) {
            if (cartIdx[i] == idx) {
                return i;
            }
        }
        return -1;
    }

    static int quantityInCart(int idx) {
        int pos = positionInCart(idx);
        return (pos == -1) ? 0 : cartQty[pos];
    }

    static void removeSelected() {
        int row = cartTable.getSelectedRow();
        if (row < 0) {
            error("Select a row in the bill to remove.");
            return;
        }
        for (int i = row; i < cartCount - 1; i++) {
            cartIdx[i] = cartIdx[i + 1];
            cartQty[i] = cartQty[i + 1];
        }
        cartCount--;
        refreshCart();
    }

    static void cancelBill() {
        if (cartCount == 0) {
            custField.setText("");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(frame, "Cancel this bill? All added medicines will be removed.",
                "Cancel Bill", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            cartCount = 0;
            custField.setText("");
            refreshCart();
        }
    }

    static double calculateTotal() {
        double sum = 0;
        for (int i = 0; i < cartCount; i++) {
            sum += prices[cartIdx[i]] * cartQty[i];
        }
        return sum;
    }

    static void generateBill() {
        String customer = custField.getText().trim();
        if (customer.isEmpty()) {
            error("Please enter the customer name.");
            return;
        }
        if (cartCount == 0) {
            error("Add at least one medicine to the bill.");
            return;
        }
        double total = calculateTotal();
        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));

        JTextArea area = new JTextArea(buildBillText(customer, dateTime, total) + "\n\nConfirm sale and update stock?");
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setEditable(false);
        int ok = JOptionPane.showConfirmDialog(frame, area, "Bill Preview", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (ok == JOptionPane.YES_OPTION) {
            String alerts = updateStock();
            recordTransaction(dateTime, customer, buildSummary(), total);
            saveData();
            cartCount = 0;
            custField.setText("");
            refreshCart();
            refreshAll();
            info("Sale completed! Bill No " + histCount + " saved to history." + alerts);
        }
    }

    static String buildBillText(String customer, String dateTime, double total) {
        StringBuilder sb = new StringBuilder();
        sb.append("================ PHARMACY BILL ================\n");
        sb.append("Bill No : ").append(histCount + 1).append("\n");
        sb.append("Date    : ").append(dateTime).append("\n");
        sb.append("Customer: ").append(customer).append("\n");
        sb.append("-----------------------------------------------\n");
        sb.append(String.format("%-3s %-18s %4s %8s %10s\n", "No", "Medicine", "Qty", "Price", "Amount"));
        for (int i = 0; i < cartCount; i++) {
            int idx = cartIdx[i];
            sb.append(String.format("%-3d %-18s %4d %8.2f %10.2f\n",
                    (i + 1), names[idx], cartQty[i], prices[idx], prices[idx] * cartQty[i]));
        }
        sb.append("-----------------------------------------------\n");
        sb.append(String.format("TOTAL PAYABLE : Rs. %.2f\n", total));
        sb.append("===============================================");
        return sb.toString();
    }

    // ================= Stock update =================
    static String updateStock() {
        String alerts = "";
        for (int i = 0; i < cartCount; i++) {
            int idx = cartIdx[i];
            stock[idx] -= cartQty[i];
            if (stock[idx] <= LOW_STOCK_LIMIT) {
                alerts += "\n\nALERT: " + names[idx] + " is low on stock (" + stock[idx] + " left). Needs restocking.";
            }
        }
        return alerts;
    }

    static void restockSelected() {
        int row = restockTable.getSelectedRow();
        if (row < 0) {
            error("Select a medicine from the list to restock.");
            return;
        }
        int idx = Integer.parseInt(restockModel.getValueAt(row, 0).toString()) - 1;
        String input = JOptionPane.showInputDialog(frame, "Quantity to add for " + names[idx] + ":");
        if (input == null) {
            return;
        }
        try {
            int qty = Integer.parseInt(input.trim());
            if (qty <= 0) {
                error("Quantity must be greater than 0.");
                return;
            }
            stock[idx] += qty;
            saveData();
            refreshAll();
            info(names[idx] + " restocked. New stock = " + stock[idx]);
        } catch (NumberFormatException e) {
            error("Please enter a valid whole number.");
        }
    }

    // ================= Search (linear): number -> by ID, text -> by name =================
    static void searchMedicine() {
        String key = searchField.getText().trim().toLowerCase();
        searchModel.setRowCount(0);
        if (key.isEmpty()) {
            error("Enter a medicine name or ID to search.");
            return;
        }
        boolean byId = key.matches("\\d{1,6}");
        for (int i = 0; i < count; i++) {
            boolean match = byId ? (i + 1 == Integer.parseInt(key)) : names[i].toLowerCase().contains(key);
            if (match) {
                searchModel.addRow(new Object[]{i + 1, names[i], String.format("%.2f", prices[i]), stock[i]});
            }
        }
        if (searchModel.getRowCount() == 0) {
            info("Medicine not found.");
        }
    }

    static int findByName(String name) {
        for (int i = 0; i < count; i++) {
            if (names[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    // ================= Transaction history =================
    static String buildSummary() {
        String s = "";
        for (int i = 0; i < cartCount; i++) {
            s += names[cartIdx[i]] + " x" + cartQty[i] + (i < cartCount - 1 ? ", " : "");
        }
        return s;
    }

    static void recordTransaction(String date, String customer, String items, double total) {
        if (histCount >= MAX_BILLS) {
            return;
        }
        histDate[histCount] = date;
        histCustomer[histCount] = customer;
        histItems[histCount] = items;
        histTotal[histCount] = total;
        histCount++;
    }

    // ================= Refresh screens =================
    static String statusOf(int qty) {
        return qty == 0 ? "OUT OF STOCK" : qty <= LOW_STOCK_LIMIT ? "LOW STOCK" : "Available";
    }

    static void refreshAll() {
        refreshStock();
        refreshMedBox();
        refreshRestock();
        refreshHistory();
    }

    static void refreshStock() {
        stockModel.setRowCount(0);
        for (int i = 0; i < count; i++) {
            stockModel.addRow(new Object[]{i + 1, names[i], String.format("%.2f", prices[i]), stock[i], statusOf(stock[i])});
        }
    }

    static void refreshRestock() {
        restockModel.setRowCount(0);
        for (int i = 0; i < count; i++) {
            if (stock[i] <= LOW_STOCK_LIMIT) {
                restockModel.addRow(new Object[]{i + 1, names[i], stock[i], statusOf(stock[i])});
            }
        }
    }

    static void refreshMedBox() {
        int previous = medBox.getSelectedIndex();
        medBox.removeAllItems();
        for (int i = 0; i < count; i++) {
            medBox.addItem((i + 1) + " - " + names[i] + "  (Rs." + String.format("%.2f", prices[i]) + ", stock " + stock[i] + ")");
        }
        if (previous >= 0 && previous < count) {
            medBox.setSelectedIndex(previous);
        }
    }

    static void refreshCart() {
        cartModel.setRowCount(0);
        for (int i = 0; i < cartCount; i++) {
            int idx = cartIdx[i];
            cartModel.addRow(new Object[]{names[idx], cartQty[i], String.format("%.2f", prices[idx]),
                    String.format("%.2f", prices[idx] * cartQty[i])});
        }
        totalLabel.setText(String.format("Total: Rs. %.2f", calculateTotal()));
    }

    static void refreshHistory() {
        if (histCount == 0) {
            historyArea.setText("No transactions yet.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        double grandTotal = 0;
        for (int i = 0; i < histCount; i++) {
            sb.append("Bill No ").append(i + 1).append("  |  ").append(histDate[i]).append("  |  ").append(histCustomer[i]).append("\n");
            sb.append("   Items : ").append(histItems[i]).append("\n");
            sb.append(String.format("   Total : Rs. %.2f\n", histTotal[i]));
            sb.append("--------------------------------------------------\n");
            grandTotal += histTotal[i];
        }
        sb.append("Total bills : ").append(histCount).append("\n");
        sb.append(String.format("Total sales : Rs. %.2f\n", grandTotal));
        historyArea.setText(sb.toString());
    }

    // ================= Navigation (switch) =================
    static void navigate(String cmd) {
        switch (cmd) {
            case "STOCK":
                refreshStock();
                break;
            case "BILLING":
                refreshMedBox();
                break;
            case "RESTOCK":
                refreshRestock();
                break;
            case "HISTORY":
                refreshHistory();
                break;
            case "EXIT":
                int ok = JOptionPane.showConfirmDialog(frame, "Exit the Pharmacy System?", "Exit", JOptionPane.YES_NO_OPTION);
                if (ok == JOptionPane.YES_OPTION) {
                    saveData();
                    System.exit(0);
                }
                return;
            default:
                break;
        }
        cards.show(content, cmd);
    }

    // ================= GUI construction =================
    static void buildGui() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            // default look and feel is fine
        }

        frame = new JFrame("Pharmacy Billing & Stock Management System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1050, 660);
        frame.setLocationRelativeTo(null);

        JLabel banner = new JLabel("  PHARMACY BILLING & STOCK MANAGEMENT SYSTEM");
        banner.setFont(new Font("SansSerif", Font.BOLD, 22));
        banner.setForeground(Color.WHITE);
        banner.setOpaque(true);
        banner.setBackground(TEAL);
        banner.setPreferredSize(new Dimension(100, 56));

        String[] labels = {"Medicines & Stock", "Register Medicine", "Billing", "Search Medicine",
                "Restock Alerts", "Transaction History", "Exit"};
        String[] cmds = {"STOCK", "REGISTER", "BILLING", "SEARCH", "RESTOCK", "HISTORY", "EXIT"};
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(NAVY);
        sidebar.setBorder(BorderFactory.createEmptyBorder(15, 10, 10, 10));
        for (int i = 0; i < labels.length; i++) {
            final String cmd = cmds[i];
            JButton b = button(labels[i], NAVY, () -> navigate(cmd));
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            sidebar.add(b);
            sidebar.add(Box.createVerticalStrut(6));
        }

        content.add(buildStockPage(), "STOCK");
        content.add(buildRegisterPage(), "REGISTER");
        content.add(buildBillingPage(), "BILLING");
        content.add(buildSearchPage(), "SEARCH");
        content.add(buildRestockPage(), "RESTOCK");
        content.add(buildHistoryPage(), "HISTORY");

        frame.add(banner, BorderLayout.NORTH);
        frame.add(sidebar, BorderLayout.WEST);
        frame.add(content, BorderLayout.CENTER);

        refreshAll();
        refreshCart();
        cards.show(content, "STOCK");
        frame.setVisible(true);
    }

    static JPanel buildStockPage() {
        JTable table = new JTable(stockModel);
        colorStatus(table, 4);
        return page("Available Medicines & Stock", tableIn(table));
    }

    static JPanel buildRegisterPage() {
        regName = new JTextField();
        regPrice = new JTextField();
        regQty = new JTextField();

        JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
        form.add(new JLabel("Medicine name"));
        form.add(regName);
        form.add(new JLabel("Price per unit (Rs.)"));
        form.add(regPrice);
        form.add(new JLabel("Stock quantity"));
        form.add(regQty);
        form.setMaximumSize(new Dimension(480, 130));
        form.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton add = button("Register Medicine", TEAL, PharmacyManagementSystem::registerMedicine);
        add.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel hint = new JLabel("If the medicine already exists, its stock is increased instead.");
        hint.setForeground(GREY);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.add(form);
        box.add(Box.createVerticalStrut(18));
        box.add(add);
        box.add(Box.createVerticalStrut(12));
        box.add(hint);

        JPanel holder = new JPanel(new BorderLayout());
        holder.add(box, BorderLayout.NORTH);
        return page("Register New Medicine", holder);
    }

    static JPanel buildBillingPage() {
        custField = new JTextField(14);
        quickField = new JTextField(18);
        medBox = new JComboBox<>();
        qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));
        totalLabel = new JLabel("Total: Rs. 0.00");
        totalLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        totalLabel.setForeground(NAVY);
        cartTable = new JTable(cartModel);

        JPanel top = new JPanel(new GridLayout(3, 1));
        top.add(flow(FlowLayout.LEFT, 8, new JLabel("Customer name:"), custField));
        top.add(flow(FlowLayout.LEFT, 8, new JLabel("Medicine:"), medBox, new JLabel("Qty:"), qtySpinner,
                button("Add to Bill", TEAL, PharmacyManagementSystem::addSelected)));
        top.add(flow(FlowLayout.LEFT, 8, new JLabel("Quick add (ProductID:Qty, e.g.  1:5, 2:3, 3:4):"), quickField,
                button("Quick Add", TEAL, PharmacyManagementSystem::quickAdd)));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(flow(FlowLayout.LEFT, 8, button("Remove Selected", GREY, PharmacyManagementSystem::removeSelected),
                button("Cancel Bill", RED, PharmacyManagementSystem::cancelBill)), BorderLayout.WEST);
        bottom.add(flow(FlowLayout.RIGHT, 14, totalLabel,
                button("Generate Bill", NAVY, PharmacyManagementSystem::generateBill)), BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.add(top, BorderLayout.NORTH);
        body.add(tableIn(cartTable), BorderLayout.CENTER);
        body.add(bottom, BorderLayout.SOUTH);
        return page("Billing", body);
    }

    static JPanel buildSearchPage() {
        searchField = new JTextField(22);
        searchField.addActionListener(e -> searchMedicine());     // Enter key also searches

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.add(flow(FlowLayout.LEFT, 8, new JLabel("Medicine name or ID:"), searchField,
                button("Search", TEAL, PharmacyManagementSystem::searchMedicine)), BorderLayout.NORTH);
        body.add(tableIn(new JTable(searchModel)), BorderLayout.CENTER);
        return page("Search Medicine", body);
    }

    static JPanel buildRestockPage() {
        restockTable = new JTable(restockModel);
        colorStatus(restockTable, 3);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.add(tableIn(restockTable), BorderLayout.CENTER);
        body.add(flow(FlowLayout.LEFT, 8, button("Restock Selected", TEAL, PharmacyManagementSystem::restockSelected),
                new JLabel("Medicines with stock of " + LOW_STOCK_LIMIT + " or less are listed here.")), BorderLayout.SOUTH);
        return page("Restock Alerts", body);
    }

    static JPanel buildHistoryPage() {
        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        return page("Transaction History", new JScrollPane(historyArea));
    }

    // ================= GUI helpers =================
    static JPanel flow(int align, int gap, Component... items) {
        JPanel p = new JPanel(new FlowLayout(align, gap, 4));
        for (Component c : items) {
            p.add(c);
        }
        return p;
    }

    static JPanel page(String title, JComponent body) {
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("SansSerif", Font.BOLD, 24));
        heading.setForeground(NAVY);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        p.add(heading, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    static JButton button(String text, Color bg, Runnable action) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 14));
        b.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        b.addActionListener(e -> action.run());
        return b;
    }

    static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
    }

    static JScrollPane tableIn(JTable t) {
        t.setRowHeight(28);
        t.setFont(new Font("SansSerif", Font.PLAIN, 14));
        t.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 14));
        t.getTableHeader().setBackground(NAVY);
        t.getTableHeader().setForeground(Color.WHITE);
        JScrollPane scroll = new JScrollPane(t);
        scroll.setColumnHeaderView(t.getTableHeader());
        return scroll;
    }

    static void colorStatus(JTable t, int col) {
        t.getColumnModel().getColumn(col).setCellRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(tb, v, sel, foc, r, c);
                String s = String.valueOf(v);
                comp.setForeground(s.startsWith("OUT") ? RED : s.startsWith("LOW") ? new Color(230, 120, 0) : new Color(0, 130, 60));
                return comp;
            }
        });
    }

    static void error(String msg) {
        JOptionPane.showMessageDialog(frame, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    static void info(String msg) {
        JOptionPane.showMessageDialog(frame, msg, "Pharmacy System", JOptionPane.INFORMATION_MESSAGE);
    }
}