package com.alexander.alsbanker;

public class InterestFormula {

    public static double calculate(double outstanding, double principal, double rate) {
        String mode = AlsBanker.get().getConfig().getString("interest.mode", "outstanding");

        switch (mode.toLowerCase()) {
            case "principal":
                return principal * rate;
            case "average":
                return ((outstanding + principal) / 2.0) * rate;
            case "outstanding":
            default:
                return outstanding * rate;
        }
    }
}
