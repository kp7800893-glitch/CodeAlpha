import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;

public class StockTradingPlatform {

    // ====== Models ======
    static class Stock {
        final String ticker;
        final String name;
        double price;

        Stock(String ticker, String name, double price) {
            this.ticker = ticker;
            this.name = name;
            this.price = price;
        }

        @Override
        public String toString() {
            return ticker + " - " + name + " : " + fmt(price);
        }
    }

    static class Market {
        private final Map<String, Stock> stocks = new LinkedHashMap<>();
        private final Random rnd = new Random();

        void add(Stock s) { stocks.put(s.ticker, s); }
        Stock get(String ticker) { return stocks.get(ticker.toUpperCase()); }
        Collection<Stock> all() { return stocks.values(); }

        // Simulate daily random-walk: +/- up to ~4%
        void tickDay() {
            for (Stock s : stocks.values()) {
                double drift = 0.000;                    // no bias
                double shock = rnd.nextGaussian() * 0.02; // ~2% std dev
                double change = 1 + drift + shock;
                s.price = Math.max(0.5, s.price * change); // floor at 0.5
            }
        }
    }

    static class Holding {
        final String ticker;
        int qty;
        double avgCost; // weighted average cost per share

        Holding(String ticker, int qty, double avgCost) {
            this.ticker = ticker;
            this.qty = qty;
            this.avgCost = avgCost;
        }
    }

    static class Transaction {
        final LocalDate date;
        final String type; // BUY/SELL
        final String ticker;
        final int qty;
        final double price;
        final double total;

        Transaction(LocalDate date, String type, String ticker, int qty, double price) {
            this.date = date;
            this.type = type;
            this.ticker = ticker;
            this.qty = qty;
            this.price = price;
            this.total = price * qty;
        }
    }

    static class Portfolio {
        double cash;
        final Map<String, Holding> holdings = new LinkedHashMap<>();
        final List<Transaction> history = new ArrayList<>();
        final List<ValuePoint> valueHistory = new ArrayList<>();

        Portfolio(double initialCash) { this.cash = initialCash; }

        double marketValue(Market market) {
            double v = cash;
            for (Holding h : holdings.values()) {
                Stock s = market.get(h.ticker);
                if (s != null) v += h.qty * s.price;
            }
            return v;
        }

        void recordValue(LocalDate date, Market market) {
            valueHistory.add(new ValuePoint(date, marketValue(market)));
        }

        boolean buy(Market market, String ticker, int qty) {
            Stock s = market.get(ticker);
            if (s == null || qty <= 0) return false;
            double cost = s.price * qty;
            if (cost > cash) return false;

            cash -= cost;
            Holding h = holdings.getOrDefault (s.ticker, new Holding(s.ticker, 0, 0.0));
            double totalCostBefore = h.avgCost * h.qty;
            h.qty += qty;
            h.avgCost = (totalCostBefore + cost) / h.qty;
            holdings.put(s.ticker, h);
            history.add(new Transaction(LocalDate.now(), "BUY", s.ticker, qty, s.price));
            return true;
        }

        boolean sell(Market market, String ticker, int qty) {
            Holding h = holdings.get(ticker.toUpperCase());
            Stock s = market.get(ticker);
            if (h == null || s == null || qty <= 0 || qty > h.qty) return false;

            double proceeds = s.price * qty;
            cash += proceeds;
            h.qty -= qty;
            if (h.qty == 0) holdings.remove(h.ticker);
            history.add(new Transaction(LocalDate.now(), "SELL", s.ticker, qty, s.price));
            return true;
        }
    }

    static class ValuePoint {
        final LocalDate date;
        final double value;
        ValuePoint(LocalDate d, double v) { date = d; value = v; }
    }

    static class Store {
        private static final String PORTFOLIO_FILE = "portfolio.csv";
        private static final String TX_FILE = "transactions.csv";
        private static final String PERF_FILE = "performance.csv";

        static void save(Portfolio pf) {
            // portfolio.csv: first row cash, then holdings as ticker,qty,avgCost
            try (BufferedWriter bw = Files.newBufferedWriter(Path.of(PORTFOLIO_FILE), StandardCharsets.UTF_8)) {
                bw.write("CASH," + pf.cash);
                bw.newLine();
                for (Holding h : pf.holdings.values()) {
                    bw.write(h.ticker + "," + h.qty + "," + h.avgCost);
                    bw.newLine();
                }
            } catch (IOException e) { e.printStackTrace(); }

            // transactions.csv
            try (BufferedWriter bw = Files.newBufferedWriter(Path.of(TX_FILE), StandardCharsets.UTF_8)) {
                bw.write("date,type,ticker,qty,price,total");
                bw.newLine();
                for (Transaction t : pf.history) {
                    bw.write(t.date + "," + t.type + "," + t.ticker + "," + t.qty + "," + t.price + "," + t.total);
                    bw.newLine();
                }
            } catch (IOException e) { e.printStackTrace(); }

            // performance.csv
            try (BufferedWriter bw = Files.newBufferedWriter(Path.of(PERF_FILE), StandardCharsets.UTF_8)) {
                bw.write("date,value");
                bw.newLine();
                for (ValuePoint vp : pf.valueHistory) {
                    bw.write(vp.date + "," + vp.value);
                    bw.newLine();
                }
            } catch (IOException e) { e.printStackTrace(); }
        }

        static Portfolio loadOrNew(double defaultCash) {
            Portfolio pf = new Portfolio(defaultCash);

            if (Files.exists(Path.of("portfolio.csv"))) {
                try (BufferedReader br = Files.newBufferedReader(Path.of("portfolio.csv"), StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] p = line.split(",");
                        if (p.length == 0) continue;
                        if (p[0].equalsIgnoreCase("CASH") && p.length >= 2) {
                            pf.cash = Double.parseDouble(p[1]);
                        } else if (p.length >= 3) {
                            String tick = p[0].toUpperCase();
                            int qty = Integer.parseInt(p[1]);
                            double avg = Double.parseDouble(p[2]);
                            pf.holdings.put(tick, new Holding(tick, qty, avg));
                        }
                    }
                } catch (IOException e) { e.printStackTrace(); }
            }

            if (Files.exists(Path.of("transactions.csv"))) {
                try (BufferedReader br = Files.newBufferedReader(Path.of("transactions.csv"), StandardCharsets.UTF_8)) {
                    String line = br.readLine(); // header
                    while ((line = br.readLine()) != null) {
                        String[] p = line.split(",");
                        if (p.length >= 6) {
                            LocalDate d = LocalDate.parse(p[0]);
                            pf.history.add(new Transaction(d, p[1], p[2], Integer.parseInt(p[3]),
                                    Double.parseDouble(p[4])));
                        }
                    }
                } catch (IOException e) { e.printStackTrace(); }
            }

            if (Files.exists(Path.of("performance.csv"))) {
                try (BufferedReader br = Files.newBufferedReader(Path.of("performance.csv"), StandardCharsets.UTF_8)) {
                    String line = br.readLine(); // header
                    while ((line = br.readLine()) != null) {
                        String[] p = line.split(",");
                        if (p.length >= 2) {
                            pf.valueHistory.add(new ValuePoint(LocalDate.parse(p[0]), Double.parseDouble(p[1])));
                        }
                    }
                } catch (IOException e) { e.printStackTrace(); }
            }

            return pf;
        }
    }

    // ====== App ======
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        // Create market with a few tickers
        Market market = new Market();
        market.add(new Stock("AAPL", "Apple Inc.", 180.00));
        market.add(new Stock("GOOG", "Alphabet Inc.", 135.00));
        market.add(new Stock("AMZN", "Amazon.com Inc.", 150.00));
        market.add(new Stock("TSLA", "Tesla Inc.", 220.00));
        market.add(new Stock("NFLX", "Netflix Inc.", 480.00));

        // Load or create portfolio
        Portfolio pf = Store.loadOrNew(10_000.00);
        LocalDate today = LocalDate.now();
        pf.recordValue(today, market); // ensure at least one point

        int choice;
        do {
            System.out.println("\n=== STOCK TRADING PLATFORM ===");
            System.out.println("Cash Balance : $" + fmt(pf.cash));
            System.out.println("1) View Market");
            System.out.println("2) View Portfolio");
            System.out.println("3) Buy Stock");
            System.out.println("4) Sell Stock");
            System.out.println("5) Advance 1 Day (simulate prices)");
            System.out.println("6) View Transactions");
            System.out.println("7) View Performance History");
            System.out.println("8) Save & Exit");
            System.out.print("Enter choice: ");
            while (!sc.hasNextInt()) { sc.next(); System.out.print("Enter choice: "); }
            choice = sc.nextInt();
            sc.nextLine();

            switch (choice) {
                case 1 -> showMarket(market);
                case 2 -> showPortfolio(pf, market);
                case 3 -> doBuy(sc, pf, market);
                case 4 -> doSell(sc, pf, market);
                case 5 -> {
                    market.tickDay();
                    LocalDate d = LocalDate.now();
                    pf.recordValue(d, market);
                    System.out.println("✅ Advanced one day. Prices updated. Date: " + d);
                }
                case 6 -> showTransactions(pf);
                case 7 -> showPerformance(pf);
                case 8 -> {
                    Store.save(pf);
                    System.out.println("💾 Data saved. Goodbye!");
                }
                default -> System.out.println("Invalid option.");
            }
        } while (choice != 8);

        sc.close();
    }

    // ====== UI helpers ======
    private static void showMarket(Market market) {
        System.out.println("\n--- MARKET ---");
        System.out.printf("%-8s %-24s %12s%n", "Ticker", "Name", "Price ($)");
        for (Stock s : market.all()) {
            System.out.printf("%-8s %-24s %12s%n", s.ticker, s.name, fmt(s.price));
        }
    }

    private static void showPortfolio(Portfolio pf, Market market) {
        System.out.println("\n--- PORTFOLIO ---");
        System.out.println("Cash: $" + fmt(pf.cash));
        if (pf.holdings.isEmpty()) {
            System.out.println("(No holdings)");
        } else {
            System.out.printf("%-8s %8s %12s %12s %12s%n",
                    "Ticker", "Qty", "Avg Cost", "Last Price", "Position($)");
            for (Holding h : pf.holdings.values()) {
                Stock s = market.get(h.ticker);
                double pos = s.price * h.qty;
                System.out.printf("%-8s %8d %12s %12s %12s%n",
                        h.ticker, h.qty, fmt(h.avgCost), fmt(s.price), fmt(pos));
            }
        }
        System.out.println("Total Portfolio Value: $" + fmt(pf.marketValue(market)));
    }

    private static void doBuy(Scanner sc, Portfolio pf, Market market) {
        System.out.print("Enter ticker to BUY: ");
        String t = sc.nextLine().toUpperCase();
        Stock s = market.get(t);
        if (s == null) { System.out.println("Unknown ticker."); return; }
        System.out.print("Enter quantity: ");
        int q = readInt(sc);
        if (q <= 0) { System.out.println("Invalid quantity."); return; }

        if (pf.buy(market, t, q)) {
            System.out.println("✅ Bought " + q + " " + t + " @ $" + fmt(s.price));
        } else {
            System.out.println("❌ Purchase failed (insufficient cash or invalid qty).");
        }
    }

    private static void doSell(Scanner sc, Portfolio pf, Market market) {
        System.out.print("Enter ticker to SELL: ");
        String t = sc.nextLine().toUpperCase();
        Stock s = market.get(t);
        if (s == null) { System.out.println("Unknown ticker."); return; }
        System.out.print("Enter quantity: ");
        int q = readInt(sc);
        if (q <= 0) { System.out.println("Invalid quantity."); return; }

        if (pf.sell(market, t, q)) {
            System.out.println("✅ Sold " + q + " " + t + " @ $" + fmt(s.price));
        } else {
            System.out.println("❌ Sell failed (not enough shares or invalid qty).");
        }
    }

    private static void showTransactions(Portfolio pf) {
        System.out.println("\n--- TRANSACTIONS ---");
        if (pf.history.isEmpty()) { System.out.println("(No transactions)"); return; }
        System.out.printf("%-12s %-6s %-8s %8s %12s %12s%n",
                "Date", "Type", "Ticker", "Qty", "Price", "Total");
        for (Transaction t : pf.history) {
            System.out.printf("%-12s %-6s %-8s %8d %12s %12s%n",
                    t.date, t.type, t.ticker, t.qty, fmt(t.price), fmt(t.total));
        }
    }

    private static void showPerformance(Portfolio pf) {
        System.out.println("\n--- PERFORMANCE (Portfolio Value Over Time) ---");
        if (pf.valueHistory.isEmpty()) { System.out.println("(No data)"); return; }
        System.out.printf("%-12s %14s%n", "Date", "Value ($)");
        for (ValuePoint vp : pf.valueHistory) {
            System.out.printf("%-12s %14s%n", vp.date, fmt(vp.value));
        }
        System.out.println("(Tip: performance also saved to performance.csv)");
    }

    private static int readInt(Scanner sc) {
        while (!sc.hasNextInt()) { sc.next(); System.out.print("Enter a valid number: "); }
        int v = sc.nextInt();
        sc.nextLine();
        return v;
    }

    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");
    private static String fmt(double x) { return DF.format(x); }
}
