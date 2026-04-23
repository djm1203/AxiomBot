package com.axiom.api.util;

/**
 * Small helper for common production-script timing.
 *
 * It handles two repeated patterns:
 * 1. waiting for the make-all dialog to appear after an interaction
 * 2. waiting for a batch animation to start and later finish
 *
 * Scripts still own state transitions and material-specific checks. This class
 * only removes duplicated tick-counter logic.
 */
public final class ProductionTickTracker
{
    private int dialogWaitTicks;
    private boolean wasAnimating;
    private int noAnimationTicks;

    public void resetDialog()
    {
        dialogWaitTicks = 0;
    }

    public DialogStatus observeDialog(boolean dialogOpen, int timeoutTicks)
    {
        if (dialogOpen)
        {
            dialogWaitTicks = 0;
            return DialogStatus.OPEN;
        }

        dialogWaitTicks++;
        return dialogWaitTicks >= timeoutTicks ? DialogStatus.TIMED_OUT : DialogStatus.WAITING;
    }

    public int getDialogWaitTicks()
    {
        return dialogWaitTicks;
    }

    public void resetAnimation()
    {
        wasAnimating = false;
        noAnimationTicks = 0;
    }

    public BatchStatus observeAnimation(boolean animating, int timeoutTicks)
    {
        if (animating)
        {
            noAnimationTicks = 0;
            if (!wasAnimating)
            {
                wasAnimating = true;
                return BatchStatus.STARTED;
            }
            return BatchStatus.IN_PROGRESS;
        }

        noAnimationTicks++;
        if (wasAnimating)
        {
            wasAnimating = false;
            return BatchStatus.COMPLETED;
        }

        return noAnimationTicks >= timeoutTicks ? BatchStatus.TIMEOUT : BatchStatus.WAITING_FOR_START;
    }

    public int getNoAnimationTicks()
    {
        return noAnimationTicks;
    }

    public enum DialogStatus
    {
        OPEN,
        WAITING,
        TIMED_OUT
    }

    public enum BatchStatus
    {
        STARTED,
        IN_PROGRESS,
        COMPLETED,
        WAITING_FOR_START,
        TIMEOUT
    }
}
