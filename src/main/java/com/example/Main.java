package com.example;

import com.example.api.ElpriserAPI;
import com.example.api.ElpriserAPI.Prisklass;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.text.DecimalFormat;

import com.example.api.ElpriserAPI.Elpris;


public class Main {
    private static final DecimalFormat ORE_FORMAT = new DecimalFormat("0.00");
    private static final DateTimeFormatter FORMAT_HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMAT_HH = DateTimeFormatter.ofPattern("HH");

    private record ParsedArguments(
            Prisklass zone,
            LocalDate date,
            ChargingTime chargingTime,
            boolean sorted,
            boolean help
    ) {}

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
            for (ChargingTime ct : ChargingTime.values()) {
                if (ct.label.equalsIgnoreCase(label)) {
                    return ct;
                }
            }
            throw new IllegalArgumentException("Ogiltigt värde för laddtid: " + label);
        }

    }
    private static final DateTimeFormatter TIME_FORMAT_HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_FORMAT_HH = DateTimeFormatter.ofPattern("HH");

    public static void main(String[] args) {
        Locale.setDefault(Locale.of("sv","se"));
        Map<String, String> argMap = new HashMap<>();
        Set<String> flags = new HashSet<>();
        Prisklass valdPrisklass = null;
        LocalDate valdDatum = LocalDate.now();
        ChargingTime valdLaddtid = null;

        if (args.length == 0) {
            printHelp();
            return;
        }

        ParsedArguments parsed; // All parsing sker nu i egen metod.
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
            System.out.println("Argument zone is required. Du måste ange ett elprisområde med --zone (SE1, SE2, SE3 eller SE4).");
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
                    if (i + 1 < args.length && !args[i + 1].startsWith("--") && !args[i + 1].isBlank()) {
                        String zoneInput = args[++i].toUpperCase();
                        try {
                            zone = Prisklass.valueOf(zoneInput);
                        } catch (IllegalArgumentException e) {
                            errors.add("Ogiltig zon för elprisområde. Giltiga elprisområden är SE1, SE2, SE3, SE4.");
                        }
                    } else {
                        errors.add("Du måste ange ett elprisområde efter --zone.");
                    }
                }
                case "--date" -> {
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        String dateInput = args[++i];
                        try {
                            date = LocalDate.parse(dateInput, DateTimeFormatter.ISO_LOCAL_DATE);
                        } catch (DateTimeException e) {
                            errors.add("Ogiltigt datum. Använd formatet YYYY-MM-DD.");
                        }
                    } else {
                        errors.add("Du behöver skriva ett datum med formatet YYYY-MM-DD efter --date.\n" +
                                "Om du vill ha priserna för dagens datum så kan du köra programmet utan --date.");
                    }
                }
                case "--charging" -> {
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        String chargingArg = args[++i].toLowerCase();
                        try {
                            chargingTime = ChargingTime.fromLabel(chargingArg);
                        } catch (IllegalArgumentException e) {
                            errors.add(e.getMessage());
                        }
                    }else {
                        errors.add("Du måste ange laddtid efter --charging (t.ex. 2h, 4h, 8h).");
                    }
                }
                case "--sorted" -> sorted = true;
                case "--help" -> help = true;
                default -> errors.add("Unknown argument: " + arg);
            }
        }
        if(!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }
        return new ParsedArguments(zone, date, chargingTime, sorted, help);
    }

    public static void printPriser(ElpriserAPI elpriser, LocalDate valdDatum, Prisklass valdPrisklass, boolean sorterat) {
        List<Elpris> priser = elpriser.getPriser(valdDatum, valdPrisklass);
        double max = priser.stream().mapToDouble(Elpris::sekPerKWh).max().orElse(0);
        double min = priser.stream().mapToDouble(Elpris::sekPerKWh).min().orElse(0);
        double avg = priser.stream().mapToDouble(Elpris::sekPerKWh).average().orElse(0);

        if (priser.isEmpty()) {
            System.out.println("Inga priser tillgängliga för " + valdDatum + " i område " + valdPrisklass);
            return;
        }
        if (sorterat) {
            priser.sort(Comparator.comparingDouble(Elpris::sekPerKWh).thenComparing(Elpris::timeStart));
        }
        System.out.printf("Elpriser för %s i område %s (%d st):\n", valdDatum, valdPrisklass, priser.size());

        for (Elpris pris : priser) {
            String start = pris.timeStart().format(FORMAT_HH);
            String end = pris.timeEnd().format(FORMAT_HH);
            double örePris = pris.sekPerKWh() * 100;
            System.out.printf("%s-%s %s öre\n", start, end, ORE_FORMAT.format(örePris));
        }
        if (!sorterat) {
            System.out.println("\nStatistik:");
            System.out.printf("Högsta pris: %s öre\n", ORE_FORMAT.format(max * 100));
            System.out.printf("Lägsta pris: %s öre\n", ORE_FORMAT.format(min * 100));
            System.out.printf("Medelpris:  %s öre\n", ORE_FORMAT.format(avg * 100));
        }

    }
    public static void chargeWindow(ElpriserAPI elpriser,Prisklass valdPrisklass,LocalDate valtDatum, ChargingTime chargingTime) {
        int antalTimmar = chargingTime.hours;
        List<Elpris> priserIdag = elpriser.getPriser(valtDatum, valdPrisklass);
        List<Elpris> priserImorgon = elpriser.getPriser(valtDatum.plusDays(1), valdPrisklass);

        List<Elpris> allaPriser = new ArrayList<>();
        allaPriser.addAll(priserIdag);
        allaPriser.addAll(priserImorgon);

        if (allaPriser.size() < antalTimmar) {
            System.out.println("För få datapunkter för att hitta ett laddfönster på " + antalTimmar + " timmar.");
            return;
        }
        double lägstaSummaPris;
        int startIndex = -1;

        double currentSum = 0;

// Initiera summan med de första "antalTimmar" priserna
        for (int i = 0; i < antalTimmar; i++) {
            currentSum += allaPriser.get(i).sekPerKWh();
        }
        lägstaSummaPris = currentSum;
        startIndex = 0;

// Flytta fönstret steg för steg
        for (int i = 1; i <= allaPriser.size() - antalTimmar; i++) {
            currentSum = currentSum
                    - allaPriser.get(i - 1).sekPerKWh()          // ta bort första timmen i förra fönstret
                    + allaPriser.get(i + antalTimmar - 1).sekPerKWh(); // lägg till ny timme i nya fönstret

            if (currentSum < lägstaSummaPris) { // Om det aktuella fönstret har lägre totalpris än tidigare fönster.x
                lägstaSummaPris = currentSum; //Den nya lägre summan sparas som bästa pris.
                startIndex = i; // Sparar vilket index det billigaste fönstret börjar på.
            }
        }


        if (startIndex == -1) {
            System.out.println("Kunde inte hitta ett optimalt laddningsfönster.");
            return;
        }
        String laddningStartTid = allaPriser.get(startIndex).timeStart().format(TIME_FORMAT_HH_MM);
        System.out.printf("Påbörja laddning kl %s\n", laddningStartTid);

        System.out.printf("\nBilligaste laddfönstret för %dh:\n", antalTimmar);
        for (int i = 0; i < antalTimmar; i++) {
            Elpris pris = allaPriser.get(startIndex + i);
            String start = pris.timeStart().format(FORMAT_HH);
            String end = pris.timeEnd().format(FORMAT_HH);
            double ore = pris.sekPerKWh() * 100;
            String oreFormatted = ORE_FORMAT.format(ore);
            System.out.printf("%s-%s %s öre\n", start, end, oreFormatted);
        }

        double genomsnittSek = lägstaSummaPris / antalTimmar;
        double genomsnittOre = genomsnittSek * 100;
        System.out.printf("Medelpris för fönster: %.2f öre\n", genomsnittOre);

    }

    public static void printHelp(){
        System.out.println("""
        \nDet här är ett CLI-program (Command-line interface) som hjälper användaren att optimera sin elförbrukning.
        Usage: Programmet körs genom att skriva "java -cp target/classes com.example.Main" i en kommandotolk följt av alternativen nedan:

        --zone <SE1|SE2|SE3|SE4>    Obligatoriskt. Ange  elprisområde.
        --date <YYYY-MM-DD>         Valfritt. Hämtar priser för ett specifikt datum (Priser för dagens datum om inget anges).
        --sorted                    Valfritt. Visar priser från högsta till lägsta.
        --charging <2h|4h|8h>       Valfritt. Räknar ut optimala laddningsfönstret under två, fyra eller åtta timmar för elbil.
        --help                      Visar denna hjälpinformation.
        
        Exempel:
        java -cp target/classes com.example.Main --zone SE3
        java -cp target/classes com.example.Main --zone SE4 --date 2025-09-24 --sorted
        java -cp target/classes com.example.Main --zone SE1 --charging 8h
        """);
    }
}