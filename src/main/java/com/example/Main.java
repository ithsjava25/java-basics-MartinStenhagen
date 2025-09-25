package com.example;

import com.example.api.ElpriserAPI;
import com.example.api.ElpriserAPI.Prisklass;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import com.example.api.ElpriserAPI.Elpris;


public class Main {
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

    public static void main(String[] args) {
        Map<String, String> argMap = new HashMap<>();
        Set<String> flags = new HashSet<>();
        ElpriserAPI elpriser = new ElpriserAPI();
        Prisklass valdPrisklass = null;
        LocalDate valdDatum = LocalDate.now();
        ChargingTime valdLaddtid = null;

        if (args.length == 0) {
            printHelp();
            return;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--zone":
                    if (i + 1 < args.length && !args[i + 1].startsWith("--") && !args[i + 1].isBlank()) {
                        String zoneInput = args[++i].toUpperCase();
                        try {
                            valdPrisklass = Prisklass.valueOf(zoneInput);
                            argMap.put("--zone", zoneInput);
                        } catch (IllegalArgumentException e) {
                            System.out.println("Ogiltig zon för elprisområde. Giltiga elprisområden är SE1, SE2, SE3, SE4.");
                            return;
                        }
                    } else {
                        System.out.println("Fel: Du måste ange ett elprisområde efter --zone.");
                        return;
                    }
                    break;


                case "--date":
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        String dateInput = args[++i];
                        try {
                            LocalDate datum = LocalDate.parse(dateInput, DateTimeFormatter.ISO_LOCAL_DATE);
                            argMap.put("--date", dateInput);
                            valdDatum = datum;
                        } catch (DateTimeException e) {
                            System.out.println("Ogiltigt datum. Använd formatet YYYY-MM-DD.");
                            return;
                        }
                    } else {
                        System.out.println("Du behöver skriva ett datum med formatet YYYY-MM-DD efter --date.");
                        System.out.println("Om du vill ha priserna för dagens datum så kan du köra programmet utan --date.");
                        return;
                    }
                    break;

                case "--charging":
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        String chargingArg = args[++i].toLowerCase();

                        // Kontrollera att det är en giltig tid (2h, 4h, 8h)
                        if (chargingArg.equals("2h") || chargingArg.equals("4h") || chargingArg.equals("8h")) {
                            argMap.put("--charging", chargingArg);
                        } else {
                            System.err.println("Fel: Ogiltigt värde för --charging. Tillåtna värden är 2h, 4h, eller 8h.");
                            return;
                        }
                    } else {
                        System.err.println("Fel: Du måste ange laddtid efter --charging (t.ex. 2h, 4h, 8h).");
                        return;
                    }
                    break;

                case "--sorted": //Fungerar med endast detta eftersom det finns en boolean efter switch som kollar om --sorted finns med.
                case "--help":
                    flags.add(arg);
                    break;

                default:
                    System.err.println("Unknown argument: " + arg);
                    return;
            }

        }
        if (flags.contains("--help")) {
            printHelp();
            return;
        }
        boolean sorterat = flags.contains("--sorted"); //Måste vara efter switch har körts. Om den deklareras tidigare kommer den alltid vara false.
        if (valdPrisklass == null) {
            System.out.println("Argument zone is required. Du måste ange ett elprisområde med --zone (SE1, SE2, SE3 eller SE4).");
            return;
        }
        if(argMap.containsKey("--charging")) {
            valdLaddtid = ChargingTime.fromLabel(argMap.get("--charging"));
            chargeWindow(elpriser, valdPrisklass, valdDatum, valdLaddtid);
        }else{
            printPriser(elpriser, valdDatum, valdPrisklass, sorterat);
        }



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
        // Formatter för svenska öre (kommatecken som decimal)
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("0.00", symbols);
        if (sorterat) {
            priser.sort(Comparator.comparingDouble(Elpris::sekPerKWh).thenComparing(p -> p.timeStart()));
            System.out.println("Priser sorterade från högst till lägst.");
        }
        System.out.printf("Elpriser för %s i område %s (%d st):\n", valdDatum, valdPrisklass, priser.size());
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        for (Elpris pris : priser) {
            String startTid = pris.timeStart().format(timeFormatter);
            String slutTid = pris.timeEnd().format(timeFormatter);
            String startTimme = String.format("%02d", pris.timeStart().getHour());
            String slutTimme = String.format("%02d", pris.timeEnd().getHour());
            double örePris = pris.sekPerKWh() * 100;
            System.out.printf("%s-%s %s öre\n", startTimme, slutTimme, df.format(örePris));
        }

        System.out.println("\nStatistik:");
        System.out.printf("Högsta pris: %s öre\n", df.format(max * 100));
        System.out.printf("Lägsta pris: %s öre\n", df.format(min * 100));
        System.out.printf("Medelpris:  %s öre\n", df.format(avg * 100));
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
        double lägstaSummaPris = Double.MAX_VALUE;
        int startIndex = -1;

        for (int i = 0; i <= allaPriser.size() - antalTimmar; i++) {
            double total = 0;
            for (int j = 0; j < antalTimmar; j++) {
                total += allaPriser.get(i + j).sekPerKWh();
            }
            if (total < lägstaSummaPris) {
                lägstaSummaPris = total;
                startIndex = i;
            }
        }

        if (startIndex == -1) {
            System.out.println("Kunde inte hitta ett optimalt laddningsfönster.");
            return;
        }
        DateTimeFormatter timeOnly = DateTimeFormatter.ofPattern("HH:mm");
        Elpris startPris = allaPriser.get(startIndex);
        String laddStart = startPris.timeStart().format(DateTimeFormatter.ofPattern("HH:mm"));
        String laddningStartTid = allaPriser.get(startIndex).timeStart().format(timeOnly);
        System.out.printf("Påbörja laddning kl %s\n\n", laddningStartTid);
        DateTimeFormatter hourFormatter = DateTimeFormatter.ofPattern("HH");

        System.out.printf("\nBilligaste laddfönstret för %dh:\n", antalTimmar);
        for (int i = 0; i < antalTimmar; i++) {
            Elpris pris = allaPriser.get(startIndex + i);
            String start = pris.timeStart().format(hourFormatter);
            String end = pris.timeEnd().format(hourFormatter);
            double ore = pris.sekPerKWh() * 100;
            String oreFormatted = String.format(Locale.forLanguageTag("sv-SE"), "%.2f", ore);
            System.out.printf("%s-%s %s öre\n", start, end, oreFormatted);
        }

        double genomsnittSek = lägstaSummaPris / antalTimmar;
        double genomsnittOre = genomsnittSek * 100;
        String genomsnittFormatted = String.format(Locale.forLanguageTag("sv-SE"), "%.2f", genomsnittOre);
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

