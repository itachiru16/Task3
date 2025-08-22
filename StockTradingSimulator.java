import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * StockTradingSimulator.java
 * Simple console-based stock trading simulator with OOP and file persistence.
 *
 * Save as: StockTradingSimulator.java
 * Compile: javac StockTradingSimulator.java
 * Run:     java StockTradingSimulator
 */
public class StockTradingSimulator {

    // ======= Models =======
    static class Stock {
        String symbol;
        String name;
        double price;      // current price
        double lastPrice;  // previous price (for change)

        Stock(String symbol, String name, double price) {
            this.symbol = symbol;
            this.name = name;
            this.price = price;
            this.lastPrice = price;
        }

        public double getChangePercent() {
            if (lastPrice == 0) return 0;
            return (price - lastPrice) / lastPrice * 100.0;
        }

        public void updatePriceRandomly(double maxPct) {
            lastPrice = price;
            // random change between -maxPct and +maxPct percent
            double pct = (Math.random() * 2 * maxPct) - maxPct;
            price = round(price * (1 + pct / 100.0), 2);
            if (price < 0.01) price = 0.01;
        }

        private static double round(double v, int decimals) {
            double scale = Math.pow(10, decimals);
            return Math.round(v * scale) / scale;
        }
    }

    static class Transaction {
        LocalDateTime time;
        String type; // BUY/SELL
        String symbol;
        int qty;
        double price; // price per share
        double total; // qty * price

        Transaction(String type, String symbol, int qty, double price) {
            this.time = LocalDateTime.now();
            this.type = type;
            this.symbol = symbol;
            this.qty = qty;
            this.price = price;
            this.total = round(price * qty, 2);
        }

        String toCsv() {
            return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "," + type + "," + symbol + "," + qty + "," + price + "," + total;
        }

        static Transaction fromCsv(String csv) {
            try {
                String[] p = csv.split(",", -1);
                LocalDateTime t = LocalDateTime.parse(p[0]);
                Transaction tx = new Transaction(p[1], p[2], Integer.parseInt(p[3]), Double.parseDouble(p[4]));
                tx.time = t;
                tx.total = Double.parseDouble(p[5]);
                return tx;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public String toString() {
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " " + type + " " + qty + " " + symbol + " @ " + price + " = " + total;
        }

        private static double round(double v, int decimals) {
            double scale = Math.pow(10, decimals);
            return Math.round(v * scale) / scale;
        }
    }

    static class Portfolio {
        double cash;
        Map<String, Position> positions = new HashMap<>(); // symbol -> Position
        List<Transaction> transactions = new ArrayList<>();

        Portfolio(double startingCash) {
            this.cash = startingCash;
        }

        static class Position {
            String symbol;
            int qty;
            double avgCost; // average cost per share

            Position(String symbol, int qty, double avgCost) {
                this.symbol = symbol;
                this.qty = qty;
                this.avgCost = avgCost;
            }
        }

        boolean canBuy(double cost) {
            return cash >= cost;
        }

        void buy(String symbol, int qty, double price) {
            double total = round(qty * price, 2);
            if (!canBuy(total)) throw new IllegalArgumentException("Insufficient cash.");
            cash -= total;
            Position pos = positions.get(symbol);
            if (pos == null) {
                positions.put(symbol, new Position(symbol, qty, price));
            } else {
                double newTotalCost = pos.avgCost * pos.qty + total;
                int newQty = pos.qty + qty;
                pos.avgCost = round(newTotalCost / newQty, 4);
                pos.qty = newQty;
            }
            Transaction tx = new Transaction("BUY", symbol, qty, price);
            transactions.add(tx);
        }

        void sell(String symbol, int qty, double price) {
            Position pos = positions.get(symbol);
            if (pos == null || pos.qty < qty) throw new IllegalArgumentException("Not enough shares to sell.");
            double total = round(qty * price, 2);
            pos.qty -= qty;
            if (pos.qty == 0) {
                positions.remove(symbol);
            }
            cash += total;
            Transaction tx = new Transaction("SELL", symbol, qty, price);
            transactions.add(tx);
        }

        double marketValue(Map<String, Stock> market) {
            double mv = 0.0;
            for (Position p : positions.values()) {
                Stock s = market.get(p.symbol);
                if (s != null) mv += p.qty * s.price;
            }
            return round(mv, 2);
        }

        double totalEquity(Map<String, Stock> market) {
            return round(cash + marketValue(market), 2);
        }

        void printSummary(Map<String, Stock> market) {
            System.out.println("===== Portfolio Summary =====");
            System.out.printf("Cash balance: %.2f\n", cash);
            System.out.printf("Market value of holdings: %.2f\n", marketValue(market));
            System.out.printf("Total equity: %.2f\n", totalEquity(market));
            System.out.println("Positions:");
            if (positions.isEmpty()) {
                System.out.println("  (no positions)");
            } else {
                System.out.printf("%-8s %-6s %-10s %-12s %-10s\n", "Symbol", "Qty", "AvgCost", "MktPrice", "UnrealP/L");
                for (Position p : positions.values()) {
                    Stock s = market.get(p.symbol);
                    double mkt = (s != null) ? s.price : 0.0;
                    double upl = round((mkt - p.avgCost) * p.qty, 2);
                    System.out.printf("%-8s %-6d %-10.4f %-12.2f %-10.2f\n", p.symbol, p.qty, p.avgCost, mkt, upl);
                }
            }
        }

        void printTransactions() {
            System.out.println("===== Transactions =====");
            if (transactions.isEmpty()) {
                System.out.println("  (no transactions)");
            } else {
                for (Transaction t : transactions) {
                    System.out.println(t);
                }
            }
        }

        void saveToFile(String portfolioCsv, String txCsv) throws IOException {
            try (PrintWriter pw = new PrintWriter(new FileWriter(portfolioCsv))) {
                pw.println("cash");
                pw.println(cash);
                pw.println("symbol,qty,avgCost");
                for (Position p : positions.values()) {
                    pw.println(p.symbol + "," + p.qty + "," + p.avgCost);
                }
            }
            try (PrintWriter pw = new PrintWriter(new FileWriter(txCsv))) {
                for (Transaction t : transactions) {
                    pw.println(t.toCsv());
                }
            }
        }

        static Portfolio loadFromFile(String portfolioCsv, String txCsv) throws IOException {
            Portfolio p = new Portfolio(0.0);
            File f = new File(portfolioCsv);
            if (!f.exists()) return p;
            try (Scanner sc = new Scanner(f)) {
                if (sc.hasNextLine()) {
                    String header = sc.nextLine(); // cash
                    if (sc.hasNextLine()) {
                        String cashLine = sc.nextLine().trim();
                        try { p.cash = Double.parseDouble(cashLine); } catch (Exception ex) { p.cash = 0.0; }
                    }
                }
                // skip until symbol header
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    if (line.trim().equalsIgnoreCase("symbol,qty,avgCost")) break;
                }
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.isEmpty()) continue;
                    String[] parts = line.split(",", -1);
                    if (parts.length >= 3) {
                        String sym = parts[0];
                        int qty = Integer.parseInt(parts[1]);
                        double avg = Double.parseDouble(parts[2]);
                        p.positions.put(sym, new Position(sym, qty, avg));
                    }
                }
            }
            File tf = new File(txCsv);
            if (tf.exists()) {
                try (Scanner sc = new Scanner(tf)) {
                    while (sc.hasNextLine()) {
                        String line = sc.nextLine();
                        Transaction t = Transaction.fromCsv(line);
                        if (t != null) p.transactions.add(t);
                    }
                }
            }
            return p;
        }

        private static double round(double v, int decimals) {
            double scale = Math.pow(10, decimals);
            return Math.round(v * scale) / scale;
        }
    }

    // ======= Market =======
    static class Market {
        Map<String, Stock> stocks = new LinkedHashMap<>();

        Market() {
            // initial seed stocks
            addStock(new Stock("AAPL", "Apple Inc.", 170.00));
            addStock(new Stock("GOOG", "Alphabet Inc.", 135.00));
            addStock(new Stock("MSFT", "Microsoft Corp.", 330.00));
            addStock(new Stock("AMZN", "Amazon.com Inc.", 140.00));
            addStock(new Stock("TSLA", "Tesla Inc.", 260.00));
            addStock(new Stock("INFY", "Infosys Ltd.", 18.50));
            addStock(new Stock("TCS", "Tata Consultancy", 42.00));
            addStock(new Stock("RELI", "Reliance Industries", 225.00));
            addStock(new Stock("BPCL", "BPCL", 120.00));
            addStock(new Stock("SBIN", "State Bank of India", 700.00));
        }

        void addStock(Stock s) {
            stocks.put(s.symbol, s);
        }

        Stock get(String symbol) {
            return stocks.get(symbol);
        }

        void updateAllPrices(double maxPct) {
            for (Stock s : stocks.values()) {
                s.updatePriceRandomly(maxPct);
            }
        }

        void printMarket() {
            System.out.println("===== Market =====");
            System.out.printf("%-6s %-20s %-10s %-8s\n", "Symbol", "Name", "Price", "Change%");
            for (Stock s : stocks.values()) {
                System.out.printf("%-6s %-20s %-10.2f %7.2f%%\n", s.symbol, s.name, s.price, s.getChangePercent());
            }
        }
    }

    // ======= Main Simulator Logic & UI =======
    static Scanner scanner = new Scanner(System.in);
    static Market market = new Market();
    static Portfolio portfolio;

    public static void main(String[] args) {
        System.out.println("Welcome to the Stock Trading Simulator.");
        // Load or create portfolio
        try {
            portfolio = Portfolio.loadFromFile("portfolio.csv", "transactions.csv");
            if (portfolio == null) portfolio = new Portfolio(10000.0);
            // If portfolio loaded with zero cash, give starting cash
            if (portfolio.cash <= 0.0) portfolio.cash = 10000.0;
            System.out.println("Portfolio loaded. Cash: " + portfolio.cash);
        } catch (Exception e) {
            portfolio = new Portfolio(10000.0);
            System.out.println("Starting new portfolio with $10,000.00 cash.");
        }

        boolean exit = false;
        while (!exit) {
            System.out.println("\nMenu:");
            System.out.println("1) View Market (updates prices)");
            System.out.println("2) View Market (no update)");
            System.out.println("3) Buy");
            System.out.println("4) Sell");
            System.out.println("5) View Portfolio");
            System.out.println("6) View Transactions");
            System.out.println("7) Save Portfolio");
            System.out.println("8) Load Portfolio");
            System.out.println("9) Advance Market (tick)");
            System.out.println("0) Exit");
            System.out.print("Choose: ");
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1":
                        market.updateAllPrices(5.0); // change up to +/-5%
                        market.printMarket();
                        break;
                    case "2":
                        market.printMarket();
                        break;
                    case "3":
                        handleBuy();
                        break;
                    case "4":
                        handleSell();
                        break;
                    case "5":
                        portfolio.printSummary(market.stocks);
                        break;
                    case "6":
                        portfolio.printTransactions();
                        break;
                    case "7":
                        portfolio.saveToFile("portfolio.csv", "transactions.csv");
                        System.out.println("Saved portfolio.csv and transactions.csv");
                        break;
                    case "8":
                        portfolio = Portfolio.loadFromFile("portfolio.csv", "transactions.csv");
                        System.out.println("Loaded portfolio. Cash: " + portfolio.cash);
                        break;
                    case "9":
                        market.updateAllPrices(2.0);
                        System.out.println("Market tick advanced (small random movements).");
                        break;
                    case "0":
                        exit = true;
                        break;
                    default:
                        System.out.println("Unknown option.");
                }
            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }

        // On exit, save quietly
        try {
            portfolio.saveToFile("portfolio.csv", "transactions.csv");
            System.out.println("Portfolio saved. Goodbye.");
        } catch (IOException e) {
            System.out.println("Could not save portfolio: " + e.getMessage());
        }
    }

    private static void handleBuy() {
        System.out.print("Enter symbol to buy: ");
        String sym = scanner.nextLine().trim().toUpperCase();
        Stock s = market.get(sym);
        if (s == null) {
            System.out.println("Symbol not found.");
            return;
        }
        System.out.println("Price: " + s.price);
        System.out.print("Enter quantity: ");
        int qty = Integer.parseInt(scanner.nextLine().trim());
        if (qty <= 0) {
            System.out.println("Quantity must be positive.");
            return;
        }
        double cost = qty * s.price;
        System.out.printf("Total cost: %.2f. Proceed? (y/n): ", cost);
        String ok = scanner.nextLine().trim().toLowerCase();
        if (!ok.equals("y")) {
            System.out.println("Cancelled.");
            return;
        }
        if (!portfolio.canBuy(cost)) {
            System.out.println("Insufficient cash. Cash: " + portfolio.cash);
            return;
        }
        portfolio.buy(sym, qty, s.price);
        System.out.println("Bought " + qty + " of " + sym + " @ " + s.price);
    }

    private static void handleSell() {
        System.out.print("Enter symbol to sell: ");
        String sym = scanner.nextLine().trim().toUpperCase();
        Stock s = market.get(sym);
        if (s == null) {
            System.out.println("Symbol not found.");
            return;
        }
        Portfolio.Position pos = portfolio.positions.get(sym);
        if (pos == null || pos.qty == 0) {
            System.out.println("You don't own any shares of " + sym);
            return;
        }
        System.out.println("You own: " + pos.qty + " shares. Avg cost: " + pos.avgCost);
        System.out.print("Enter quantity to sell: ");
        int qty = Integer.parseInt(scanner.nextLine().trim());
        if (qty <= 0 || qty > pos.qty) {
            System.out.println("Invalid quantity.");
            return;
        }
        double proceeds = qty * s.price;
        System.out.printf("Proceeds: %.2f. Proceed? (y/n): ", proceeds);
        String ok = scanner.nextLine().trim().toLowerCase();
        if (!ok.equals("y")) {
            System.out.println("Cancelled.");
            return;
        }
        portfolio.sell(sym, qty, s.price);
        System.out.println("Sold " + qty + " of " + sym + " @ " + s.price);
    }
}
