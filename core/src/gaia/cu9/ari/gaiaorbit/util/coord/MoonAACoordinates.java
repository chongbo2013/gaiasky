package gaia.cu9.ari.gaiaorbit.util.coord;

import java.util.Date;

import gaia.cu9.ari.gaiaorbit.util.Constants;
import gaia.cu9.ari.gaiaorbit.util.math.Vector3d;

/**
 * Coordinates of the Moon given by the algorithm in Jean Meeus' Astronomical
 * Algorithms book.
 * 
 * @author Toni Sagrista
 *
 */
public class MoonAACoordinates implements IBodyCoordinates {
    @Override
    public void doneLoading(Object... params) {
    }

    @Override
    public Vector3d getEclipticSphericalCoordinates(Date date, Vector3d out) {
        if (!Constants.withinVSOPTime(date.getTime()))
            return null;
        AstroUtils.moonEclipticCoordinates(date, out);
        // To internal units
        out.z *= Constants.KM_TO_U;
        return out;
    }

    @Override
    public Vector3d getEclipticCartesianCoordinates(Date date, Vector3d out) {
        Vector3d v = getEclipticSphericalCoordinates(date, out);
        if (v == null)
            return null;
        Coordinates.sphericalToCartesian(out, out);
        return out;
    }

    @Override
    public Vector3d getEquatorialCartesianCoordinates(Date date, Vector3d out) {
        Vector3d v = getEclipticSphericalCoordinates(date, out);
        if (v == null)
            return null;
        Coordinates.sphericalToCartesian(out, out);
        out.mul(Coordinates.equatorialToEcliptic());
        return out;
    }

}
