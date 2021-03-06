package gaia.cu9.ari.gaiaorbit.util.time;

import java.util.Date;

import gaia.cu9.ari.gaiaorbit.event.EventManager;
import gaia.cu9.ari.gaiaorbit.event.Events;
import gaia.cu9.ari.gaiaorbit.event.IObserver;
import gaia.cu9.ari.gaiaorbit.util.Constants;

/**
 * Keeps pace of the simulation time vs real time and holds the global clock. It
 * uses a time warp factor which is a multiplier to real time.
 * 
 * @author Toni Sagrista
 *
 */
public class GlobalClock implements IObserver, ITimeFrameProvider {

    private static final double MS_TO_HOUR = 1 / 3600000d;

    /** The current time of the clock **/
    public Date time, lastTime;
    /** The hour difference from the last frame **/
    public double hdiff;

    /** Represents the time wrap multiplier. Scales the real time **/
    public double timeWarp = 1;

    // Seconds since last event POST
    private float lastUpdate = 1;
    /**
     * The fixed frame rate when not in real time. Set negative to use real time
     **/
    public float fps = -1;

    /**
     * Creates a new GlobalClock
     * 
     * @param timeWrap
     *            The time wrap multiplier
     * @param date
     *            The date with which to initialise the clock
     */
    public GlobalClock(double timeWrap, Date date) {
        super();
        // Now
        this.timeWarp = timeWrap;
        hdiff = 0d;
        time = date;
        lastTime = new Date(time.getTime());
        EventManager.instance.subscribe(this, Events.PACE_CHANGE_CMD, Events.TIME_WARP_DECREASE_CMD, Events.TIME_WARP_INCREASE_CMD, Events.TIME_CHANGE_CMD);
    }

    double msacum = 0d;

    /**
     * Update function
     * 
     * @param dt
     *            Delta time in seconds
     */
    public void update(double dt) {
        if (dt != 0) {
            // In case we are in constant rate mode
            if (fps > 0) {
                dt = 1 / fps;
            }

            int sign = (int) Math.signum(timeWarp);
            double h = Math.abs(dt * timeWarp * Constants.S_TO_H);
            hdiff = h * sign;

            double ms = sign * h * Constants.H_TO_MS;

            long currentTime = time.getTime();
            lastTime.setTime(currentTime);

            long newTime = currentTime + (long) ms;
            if (newTime > Constants.MAX_TIME_MS) {
                newTime = Constants.MAX_TIME_MS;
                if (currentTime < Constants.MAX_TIME_MS) {
                    EventManager.instance.post(Events.POST_NOTIFICATION, "Maximum time reached (" + (Constants.MAX_TIME_MS * Constants.MS_TO_Y) + " years)!");
                    // Turn off time
                    EventManager.instance.post(Events.TOGGLE_TIME_CMD, false, false);
                }
            }
            if (newTime < Constants.MIN_TIME_MS) {
                newTime = Constants.MIN_TIME_MS;
                if (currentTime > Constants.MIN_TIME_MS) {
                    EventManager.instance.post(Events.POST_NOTIFICATION, "Minimum time reached (" + (Constants.MIN_TIME_MS * Constants.MS_TO_Y) + " years)!");
                    // Turn off time
                    EventManager.instance.post(Events.TOGGLE_TIME_CMD, false, false);
                }
            }

            time.setTime(newTime);

            // Post event each 1/2 second
            lastUpdate += dt;
            if (lastUpdate > .5) {
                EventManager.instance.post(Events.TIME_CHANGE_INFO, time);
                lastUpdate = 0;
            }
        } else if (time.getTime() - lastTime.getTime() != 0) {
            hdiff = (time.getTime() - lastTime.getTime()) * MS_TO_HOUR;
            lastTime.setTime(time.getTime());
        } else {
            hdiff = 0d;
        }
    }

    @Override
    public Date getTime() {
        return time;
    }

    @Override
    public void notify(Events event, Object... data) {
        switch (event) {
        case PACE_CHANGE_CMD:
            // Update pace
            this.timeWarp = (Double) data[0];
            checkTimeWarpValue();
            EventManager.instance.post(Events.PACE_CHANGED_INFO, this.timeWarp);
            break;
        case TIME_WARP_INCREASE_CMD:
            if (timeWarp == 0) {
                timeWarp = 0.125;
            } else if (timeWarp == -0.125) {
                timeWarp = 0;
            } else if (timeWarp < 0) {
                timeWarp /= 2.0;
            } else {
                timeWarp *= 2.0;
            }
            checkTimeWarpValue();
            EventManager.instance.post(Events.PACE_CHANGED_INFO, this.timeWarp);
            break;
        case TIME_WARP_DECREASE_CMD:
            if (timeWarp == 0.125) {
                timeWarp = 0;
            } else if (timeWarp == 0) {
                timeWarp = -0.125;
            } else if (timeWarp < 0) {
                timeWarp *= 2.0;
            } else {
                timeWarp /= 2.0;
            }
            checkTimeWarpValue();
            EventManager.instance.post(Events.PACE_CHANGED_INFO, this.timeWarp);
            break;
        case TIME_CHANGE_CMD:
            // Update time
            long newt = ((Date) data[0]).getTime();
            if (newt > Constants.MAX_TIME_MS) {
                newt = Constants.MAX_TIME_MS;
                EventManager.instance.post(Events.POST_NOTIFICATION, "Time overflow, set to maximum (" + (Constants.MIN_TIME_MS * Constants.MS_TO_Y) + " years)");
            }
            if (newt < Constants.MIN_TIME_MS) {
                newt = Constants.MIN_TIME_MS;
                EventManager.instance.post(Events.POST_NOTIFICATION, "Time overflow, set to minimum (" + (Constants.MIN_TIME_MS * Constants.MS_TO_Y) + " years)");
            }
            this.time.setTime(newt);
            break;
        default:
            break;
        }

    }

    private void checkTimeWarpValue() {
        if (timeWarp > Constants.MAX_WARP) {
            timeWarp = Constants.MAX_WARP;
        }
        if (timeWarp < Constants.MIN_WARP) {
            timeWarp = Constants.MIN_WARP;
        }
    }

    /**
     * Provides the time difference in hours
     */
    @Override
    public double getDt() {
        return hdiff;
    }

    @Override
    public double getWarpFactor() {
        return timeWarp;
    }

    public boolean isFixedRateMode() {
        return fps > 0;
    }

    @Override
    public float getFixedRate() {
        return fps;
    }

}
