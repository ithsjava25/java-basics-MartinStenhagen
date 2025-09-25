package com.example;

import com.example.api.ElpriserAPI;
import com.example.api.ElpriserAPI.Prisklass;
import com.example.api.ElpriserAPI.Elpris;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main {

    // Formatteringskonstanter – oberoende av systemets locale
    private static final DateTimeFormatter TIME_FORMAT_HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMAT_HH = DateTimeFormatter.ofPattern("HH");

    private static final DecimalFormat ORE_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("sv", "SE"));
        symbols.setDecimalSeparator(',');
        ORE_FORMAT = new DecimalFormat("0.00", symbols);
    }

    // Argument-packare
    private record ParsedArguments(
            Prisklass zone,
            LocalDate date,
            ChargingTime chargingTime,
            boolean sorted,
            boolean help
    ) {}

    // Laddtidsalternativ
    public enum ChargingTime {
        TWO_HOURS("2h", 2),
        FOUR_HOURS("4h", 4),
        EIGHT_HOURS("8h", 8);

        private final String label;
        private final int hours;

        ChargingTime(String label, int hours) {
            this.label = label;
            this.hours = hours;
        }

        public static ChargingTime fromLabel(String label) {
            for (ChargingTime ct : values()) {
                if (ct.label.equalsIgnoreCase(label)) {
                    return ct;
                }
            }
            throw new IllegalArgumentException("Ogiltigt värde för laddtid: " + label);
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            return;
        }

        ParsedArguments parsed;
        try {
            parsed = parseArguments(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }

        if (parsed.help()) {
            printHelp();
            return;
        }

        if (parsed.zone() == null) {
            System.out.println("Du måste ange ett elprisområde med --zone (SE1, SE2, SE3 eller SE4).");
            return;
        }

        ElpriserAPI elpriser = new ElpriserAPI();

        if (parsed.chargingTime() != null) {
            chargeWindow(elpriser, parsed.zone(), parsed.date(), parsed.chargingTime());
        } else {
            printPriser(elpriser, parsed.date(), parsed.zone(), parsed.sorted());
        }
    }

    private static ParsedArguments parseArguments(String[] args) {
        Prisklass zone = null;
        LocalDate date = LocalDate.now();
        ChargingTime chargingTime = null;
        boolean sorted = false;
        boolean help = false;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--zone" -> {
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        try {
                            zone = Prisklass.valueOf(args[++i].toUpperCase());
                        } catch (IllegalArgumentException e) {
                            errors.add("Ogiltig zon. Giltiga är SE1, SE2, SE3, SE4.");
                        }
                    } else {
                        errors.add("Du måste ange ett elprisområde efter --zone.");
                    }
                }
                case "--date" -> {
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        try {
                            date = LocalDate.parse(args[++i], DateTimeFormatter.ISO_LOCAL_DATE);
                        } catch (DateTimeException e) {
                            errors.add("Ogiltigt datum. Använd formatet YYYY-MM-DD.");
                        }
                    } else {
                        errors.add("Du måste ange ett datum efter --date.");
                    }
                }
                case "--charging" -> {
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        try {
                            chargingTime = ChargingTime.fromLabel(args[++i]);
                        } catch (IllegalArgumentException e) {
                            errors.add(e.getMessage());
                        }
                    } else {
                        errors.add("Du måste ange laddtid efter --charging (2h, 4h, 8h).");
                    }
                }
                case "--sorted" -> sorted = true;
                case "--help" -> help = true;
                default -> errors.add("Okänt argument: " + arg);
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }

        return new ParsedArguments(zone, date, chargingTime, sorted, help);
    }

    public static void printPriser(ElpriserAPI elpriser, LocalDate datum, Prisklass prisklass, boolean sorterat) {
        List<Elpris> priser = elpriser.getPriser(datum, prisklass);

        if (priser.isEmpty()) {
            System.out.println("Inga priser tillgängliga för " + datum + " i område " + prisklass);
            return;
        }

        if (sorterat) {
            priser.sort(Comparator.comparingDouble(Elpris::sekPerKWh).thenComparing(Elpris::timeStart));
        }

        System.out.printf("Elpriser för %s i område %s (%d st):\n", datum, prisklass, priser.size());

        for (Elpris pris : priser) {
            String start = pris.timeStart().format(FORMAT_HH);
            String end = pris.timeEnd().format(FORMAT_HH);
            double öre = pris.sekPerKWh() * 100;
            System.out.printf("%s-%s %s öre\n", start, end, ORE_FORMAT.format(öre));
        }

        if (!sorterat) {
            double max = priser.stream().mapToDouble(Elpris::sekPerKWh).max().orElse(0);
            double min = priser.stream().mapToDouble(Elpris::sekPerKWh).min().orElse(0);
            double avg = priser.stream().mapToDouble(Elpris::sekPerKWh).average().orElse(0);

            System.out.println("\nStatistik:");
            System.out.printf("Högsta pris: %s öre\n", ORE_FORMAT.format(max * 100));
            System.out.printf("Lägsta pris: %s öre\n", ORE_FORMAT.format(min * 100));
            System.out.printf("Medelpris:  %s öre\n", ORE_FORMAT.format(avg * 100));
        }
    }

    public static void chargeWindow(ElpriserAPI elpriser, Prisklass prisklass, LocalDate datum, ChargingTime chargingTime) {
        int timmar = chargingTime.hours;
        List<Elpris> priser = new ArrayList<>();
        priser.addAll(elpriser.getPriser(datum, prisklass));
        priser.addAll(elpriser.getPriser(datum.plusDays(1), prisklass));

        if (priser.size() < timmar) {
            System.out.println("För få datapunkter för att hitta ett laddfönster på " + timmar + " timmar.");
            return;
        }

        double lägstaSumma = 0;
        int startIndex = 0;

        for (int i = 0; i < timmar; i++) {
            lägstaSumma += priser.get(i).sekPerKWh();
        }

        double currentSum = lägstaSumma;

        for (int i = 1; i <= priser.size() - timmar; i++) {
            currentSum = currentSum - priser.get(i - 1).sekPerKWh() + priser.get(i + timmar - 1).sekPerKWh();
            if (currentSum < lägstaSumma) {
                lägstaSumma = currentSum;
                startIndex = i;
            }
        }

        String startTid = priser.get(startIndex).timeStart().format(TIME_FORMAT_HH_MM);
        System.out.printf("Påbörja laddning kl %s\n\n", startTid);
        System.out.printf("Billigaste laddfönstret för %dh:\n", timmar);

        for (int i = 0; i < timmar; i++) {
            Elpris pris = priser.get(startIndex + i);
            String start = pris.timeStart().format(FORMAT_HH);
            String end = pris.timeEnd().format(FORMAT_HH);
            double öre = pris.sekPerKWh() * 100;
            System.out.printf("%s-%s %s öre\n", start, end, ORE_FORMAT.format(öre));
        }

        double medelÖre = (lägstaSumma / timmar) * 100;
        System.out.printf("Medelpris för fönster: %.2f öre\n", medelÖre);
    }

    public static void printHelp() {
        System.out.println("""
        \\nDet här är ett CLI-program (Command-line interface) som hjälper användaren att optimera sin elförbrukning.
                Usage: Programmet körs genom att skriva "java -cp target/classes com.example.Main" i en kommandotolk följt av alternativen nedan:
       \s
                --zone <SE1|SE2|SE3|SE4>    Obligatoriskt. Ange  elprisområde.
                --date <YYYY-MM-DD>         Valfritt. Hämtar priser för ett specifikt datum (Priser för dagens datum om inget anges).
                --sorted                    Valfritt. Visar priser från högsta till lägsta.
                --charging <2h|4h|8h>       Valfritt. Räknar ut optimala laddningsfönstret under två, fyra eller åtta timmar för elbil.
                --help                      Visar denna hjälpinformation.
       \s
                Exempel:
                java -cp target/classes com.example.Main --zone SE3
                java -cp target/classes com.example.Main --zone SE4 --date 2025-09-24 --sorted
                java -cp target/classes com.example.Main --zone SE1 --charging 8h
       """);
    }
}
