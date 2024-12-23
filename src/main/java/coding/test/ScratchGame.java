package coding.test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.*;

public class ScratchGame {

    // Config classes
    private static class Config {
        public int rows;
        public int columns;
        public Map<String, Symbol> symbols;
        public Probabilities probabilities;

        @JsonProperty("win_combinations")
        public Map<String, WinCombination> winCombinations;
    }

    private static class Symbol {
        public double reward_multiplier;
        public String type;
        public Double extra;
        public String impact;
    }

    private static class Probabilities {
        public List<SymbolProbability> standard_symbols;
        public Map<String, Integer> bonus_symbols;
    }

    private static class SymbolProbability {
        public int column;
        public int row;
        public Map<String, Integer> symbols;
    }

    private static class WinCombination {
        public double reward_multiplier;
        public String when;
        public int count;
        public String group;
        public List<List<String>> covered_areas;
    }

    // Main method to run the game
    public static void main(String[] args) throws Exception {
        // Check if the arguments are provided
        if (args.length < 2) {
            System.err.println("Error: Please provide the configuration file path and bet amount.");
            System.exit(1); // Exit the program with an error code
        }

        String configFilePath = args[0];  // Get the first argument (configuration file path)
        int betAmount = Integer.parseInt(args[1]);  // Get the second argument (bet amount)

        // Load the configuration file from the resources folder
        InputStream inputStream = ScratchGame.class.getClassLoader().getResourceAsStream(configFilePath);
        if (inputStream == null) {
            System.err.println("Error: Configuration file not found in resources.");
            System.exit(1);
        }

        // Use ObjectMapper to read the configuration
        ObjectMapper objectMapper = new ObjectMapper();
        Config config = objectMapper.readValue(inputStream, Config.class);

        String[][] matrix = generateMatrix(config);

        Map<String, List<String>> appliedWinningCombinations = checkWinningCombinations(matrix, config);

        double reward = calculateReward(betAmount, appliedWinningCombinations, config);

        String appliedBonusSymbol = applyBonusSymbol(reward, config);

        printResult(matrix, reward, appliedWinningCombinations, appliedBonusSymbol);
    }

    private static String[][] generateMatrix(Config config) {
        Random random = new Random();
        String[][] matrix = new String[config.rows][config.columns];

        // Fill in the standard symbols in the matrix
        for (SymbolProbability sp : config.probabilities.standard_symbols) {
            for (Map.Entry<String, Integer> entry : sp.symbols.entrySet()) {
                String symbol = entry.getKey();
                int count = entry.getValue();
                for (int i = 0; i < count; i++) {
                    matrix[sp.row][sp.column] = symbol;
                }
            }
        }

        // Apply bonus symbols randomly in the matrix
        config.probabilities.bonus_symbols.forEach((bonusSymbol, count) -> {
            for (int i = 0; i < count; i++) {
                int row = random.nextInt(config.rows);
                int column = random.nextInt(config.columns);
                matrix[row][column] = bonusSymbol;
            }
        });

        return matrix;
    }

    private static Map<String, List<String>> checkWinningCombinations(String[][] matrix, Config config) {
        Map<String, List<String>> appliedCombinations = new HashMap<>();

        checkRows(matrix, appliedCombinations, config);
        checkColumns(matrix, appliedCombinations, config);
        checkDiagonals(matrix, appliedCombinations, config);

        return appliedCombinations;
    }

    private static void checkRows(String[][] matrix, Map<String, List<String>> appliedCombinations, Config config) {
        for (int i = 0; i < matrix.length; i++) {
            String[] row = matrix[i];
            String symbol = row[0];
            if (symbol != null && Arrays.stream(row).allMatch(s -> s.equals(symbol))) {
                addWinningCombination(symbol, "same_symbols_horizontally", appliedCombinations);
            }
        }
    }

    private static void checkColumns(String[][] matrix, Map<String, List<String>> appliedCombinations, Config config) {
        for (int i = 0; i < matrix[0].length; i++) {
            String[] column = new String[matrix.length];
            for (int j = 0; j < matrix.length; j++) {
                column[j] = matrix[j][i];
            }
            String symbol = column[0];
            if (symbol != null && column != null && Arrays.stream(column).allMatch(s -> s.equals(symbol))) {
                addWinningCombination(symbol, "same_symbols_vertically", appliedCombinations);
            }
        }
    }


    private static void checkDiagonals(String[][] matrix, Map<String, List<String>> appliedCombinations, Config config) {
        // Left-to-right diagonal
        String[] diagonalLR = new String[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            diagonalLR[i] = matrix[i][i];
        }
        if (Arrays.stream(diagonalLR).allMatch(s -> s.equals(diagonalLR[0]))) {
            addWinningCombination(diagonalLR[0], "same_symbols_diagonally_left_to_right", appliedCombinations);
        }

        // Right-to-left diagonal
        String[] diagonalRL = new String[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            diagonalRL[i] = matrix[i][matrix.length - 1 - i];
        }
        if (diagonalRL != null && diagonalRL[0] != null && Arrays.stream(diagonalRL).allMatch(s -> s.equals(diagonalRL[0]))) {
            addWinningCombination(diagonalRL[0], "same_symbols_diagonally_right_to_left", appliedCombinations);
        }
    }


    private static void addWinningCombination(String symbol, String combination, Map<String, List<String>> appliedCombinations) {
        if (!appliedCombinations.containsKey(symbol)) {
            appliedCombinations.put(symbol, new ArrayList<>());
        }
        appliedCombinations.get(symbol).add(combination);
    }


    private static double calculateReward(int betAmount, Map<String, List<String>> appliedWinningCombinations, Config config) {
        double reward = 0;

        // Calculate reward based on the symbols and their corresponding multipliers
        for (Map.Entry<String, List<String>> entry : appliedWinningCombinations.entrySet()) {
            String symbol = entry.getKey();
            double symbolMultiplier = config.symbols.get(symbol).reward_multiplier;

            for (String combination : entry.getValue()) {
                WinCombination winCombination = config.winCombinations.get(combination);
                if (winCombination != null) {
                    reward += betAmount * symbolMultiplier * winCombination.reward_multiplier;
                }
            }
        }
        return reward;
    }

    private static String applyBonusSymbol(double reward, Config config) {
        if (reward == 0) return null;

        // Check for any bonus symbols and apply them to the reward
        for (Map.Entry<String, Symbol> entry : config.symbols.entrySet()) {
            Symbol symbol = entry.getValue();
            if ("bonus".equals(symbol.type)) {
                if ("multiply_reward".equals(symbol.impact)) {
                    return entry.getKey();
                }
                if ("extra_bonus".equals(symbol.impact)) {
                    reward += symbol.extra;
                    return entry.getKey();
                }
            }
        }
        return null;
    }


    private static void printResult(String[][] matrix, double reward, Map<String, List<String>> appliedWinningCombinations, String appliedBonusSymbol) {
        System.out.println("Matrix:");
        for (String[] row : matrix) {
            System.out.println(Arrays.toString(row));
        }
        System.out.println("Reward: " + reward);
        System.out.println("Applied Winning Combinations: " + appliedWinningCombinations);
        System.out.println("Applied Bonus Symbol: " + appliedBonusSymbol);
    }
}

