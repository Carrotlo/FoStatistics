package me.foesio.foStatistics.api;

/** Result of one synchronous edit. Values are null for rejected edits. */
public record StatisticEditResult(Status status, Integer previousValue, Integer currentValue, String errorMessage) {
    public enum Status {
        CHANGED,
        UNCHANGED,
        DISABLED,
        WRONG_THREAD,
        CALLER_DISABLED,
        OFFLINE_NOT_ALLOWED,
        UNKNOWN_PLAYER,
        INVALID_AMOUNT,
        AMOUNT_TOO_LARGE,
        INVALID_STATISTIC
    }

    public boolean successful() {
        return status == Status.CHANGED || status == Status.UNCHANGED;
    }

    public static StatisticEditResult rejected(Status status, String errorMessage) {
        if (status == Status.CHANGED || status == Status.UNCHANGED) {
            throw new IllegalArgumentException("A successful status cannot be rejected.");
        }
        return new StatisticEditResult(status, null, null, errorMessage);
    }

    public static StatisticEditResult applied(int previousValue, int currentValue) {
        return new StatisticEditResult(previousValue == currentValue ? Status.UNCHANGED : Status.CHANGED,
                previousValue, currentValue, null);
    }
}
