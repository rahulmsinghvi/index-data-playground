package com.nse;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class IndexPlayground {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MMM-dd", Locale.ENGLISH);
    private static final List<Integer> ROLLING_YEARS = List.of(1, 3, 5, 7, 10, 12, 15, 17);

    private static boolean isFeb29LeapYear(LocalDate date) {
        return date.getMonthValue() == 2 && date.getDayOfMonth() == 29 && date.isLeapYear();
    }

    public void readCsvFile(String filePath) throws Exception
    {
        Map<LocalDate, Map<String,Float>> dateMap = getRawIndexData(filePath);
        Map<LocalDate, Map<String, Float>> newDateMap = getFormattedIndexMap(dateMap);

        Map<String, Map<Integer, List<Double>>> rollingReturnsStorage = calculateRollingReturns(newDateMap);

        Set<String> indexes = rollingReturnsStorage.keySet();

        // Print top-level header
        System.out.print(String.format("%s", "Metric"));
        for (String index : indexes) {
            System.out.print(String.format("\t%s", index));
        }
        System.out.println();

        for (int year : ROLLING_YEARS) {
            printMetricRow(year, "average", rollingReturnsStorage);
            printMetricRow(year, "max", rollingReturnsStorage);
            printMetricRow(year, "min", rollingReturnsStorage);
        }
    }

    private static void printMetricRow(int year, String metricType, Map<String, Map<Integer, List<Double>>> rollingReturnsStorage) {
        String metricLabel = year + " year " + metricType;
        System.out.print(String.format("%s", metricLabel));

        for (String index : rollingReturnsStorage.keySet()) {
            Map<Integer, List<Double>> returnsByYear = rollingReturnsStorage.get(index);
            double value = 0.0;

            if (returnsByYear.containsKey(year)) {
                List<Double> returns = returnsByYear.get(year);
                if ("average".equals(metricType)) {
                    value = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                } else if ("max".equals(metricType)) {
                    value = returns.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NEGATIVE_INFINITY);
                } else if ("min".equals(metricType)) {
                    value = returns.stream().mapToDouble(Double::doubleValue).min().orElse(Double.POSITIVE_INFINITY);
                }
                System.out.print(String.format("\t%.2f", value));
            } else {
                System.out.print(String.format("\t%s", "N/A"));
            }
        }
        System.out.println();
    }

    private static Map<LocalDate, Map<String, Float>> getFormattedIndexMap(Map<LocalDate, Map<String, Float>> dateMap) {
        Map<LocalDate, Map<String,Float>> newDateMap= new TreeMap<>();
        LocalDate itrDate = LocalDate.parse("2005-Mar-31", FORMATTER);
        LocalDate lastHoldingIndexMapKey = null;
        Map<String, Float> lastHoldingIndexMapValue = null;
        for (Map.Entry<LocalDate, Map<String, Float>> entry : dateMap.entrySet()) {
            itrDate = itrDate.plusDays(1);
            LocalDate keyDate = entry.getKey();

            while (itrDate.isBefore(keyDate)) {
                //System.out.println(String.format("%s: Data not available. Adding data for %s\n",itrDate.toString(), lastHoldingIndexMapKey.toString()));
                if(!isFeb29LeapYear(itrDate)) {
                    newDateMap.put(itrDate, lastHoldingIndexMapValue);
                }
                itrDate = itrDate.plusDays(1);
            }

            lastHoldingIndexMapKey = entry.getKey();
            lastHoldingIndexMapValue = entry.getValue();

            if (!isFeb29LeapYear(keyDate)) {
                newDateMap.put(entry.getKey(), entry.getValue());
            }

            Map<String, Float> value = entry.getValue();

            /*System.out.println(keyDate.toString() + ":");

            for (Map.Entry<String, String> entry2 : value.entrySet()){
                System.out.println("\t" + entry2.getKey() + ": " + entry2.getValue());
            }

            System.out.println();*/
        }

        for (Map.Entry<LocalDate, Map<String, Float>> outerEntry : newDateMap.entrySet()) {
            LocalDate dateKey = outerEntry.getKey();
            Map<String, Float> innerMap = outerEntry.getValue();

            // Print the outer key (LocalDate)
            System.out.print(dateKey);

            // Iterate over the inner map and print its key-value pairs
            for (Map.Entry<String, Float> innerEntry : innerMap.entrySet()) {
                System.out.print(","+ innerEntry.getValue());
            }
            System.out.println();
        }
        return newDateMap;
    }

    private static Map<LocalDate, Map<String,Float>> getRawIndexData(String filePath) throws IOException, CsvValidationException {
        Map<LocalDate, Map<String,Float>> dateMap = new TreeMap<>();

        String[] indexes;
                /*{"Date", "Nifty 50", "Nifty Next 50", "Nifty Alpha 50", "Nifty 500 Momentum 50",
                "Nifty 500 Multicap Momentum Quality 50", "Nifty Microcap 250"};*/

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            indexes = reader.readNext();
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                if(nextLine.length != indexes.length){
                    throw new RuntimeException("Number of indexes doesn't match for date:" + nextLine[0]);
                }
                Map<String, Float> indexMap = new LinkedHashMap<>();
                LocalDate date = null;
                for (int i = 0; i < nextLine.length; i++) {
                    if(i==0) {
                        date = LocalDate.parse(nextLine[i], FORMATTER);
                    }
                    else {
                        indexMap.put(indexes[i],Float.parseFloat(nextLine[i]));
                    }
                    if(i==indexes.length-1) {
                        dateMap.put(date, indexMap);
                    }
                }
            }
        }
        return dateMap;
    }

    // Method to calculate average rolling returns for each index
    private static Map<String, Map<Integer, List<Double>>> calculateRollingReturns(
            Map<LocalDate, Map<String, Float>> newDateMap) {

        Map<String, Map<Integer, List<Double>>> rollingReturnsStorage = new LinkedHashMap<>();

        // Iterate through each date in the dataset
        for (LocalDate currentDate : newDateMap.keySet()) {
            Map<String, Float> currentPrices = newDateMap.get(currentDate);

            // Calculate rolling returns for each index
            for (String index : currentPrices.keySet()) {
                for (int years : ROLLING_YEARS) {
                    LocalDate pastDate = currentDate.minusYears(years).plusDays(1);

                    if (newDateMap.containsKey(pastDate) && newDateMap.get(pastDate).containsKey(index)) {
                        float pastPrice = newDateMap.get(pastDate).get(index);
                        float currentPrice = currentPrices.get(index);

                        if (pastPrice > 0) {
                            // Calculate CAGR: (Current Price / Past Price)^(1/years) - 1
                            double cagr = Math.pow(currentPrice / pastPrice, 1.0 / years) - 1;
                            rollingReturnsStorage
                                    .computeIfAbsent(index, k -> new HashMap<>())
                                    .computeIfAbsent(years, k -> new ArrayList<>())
                                    .add(cagr * 100); // Convert to percentage
                        }
                    }
                }
            }
        }

        return rollingReturnsStorage;
    }

    // Method to print the average rolling returns
    private static void printAverageRollingReturns(Map<String, Map<Integer, Double>> avgRollingReturns) {
        System.out.println("\nAverage Rolling Returns:");
        for (var indexEntry : avgRollingReturns.entrySet()) {
            System.out.println("Index: " + indexEntry.getKey());
            for (var periodEntry : indexEntry.getValue().entrySet()) {
                System.out.printf("  %d-Year Average Return: %.2f%%\n", periodEntry.getKey(), periodEntry.getValue());
            }
        }
    }
}
