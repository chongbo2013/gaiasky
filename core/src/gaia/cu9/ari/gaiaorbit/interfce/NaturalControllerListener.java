package gaia.cu9.ari.gaiaorbit.interfce;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.PovDirection;
import com.badlogic.gdx.math.Vector3;

import gaia.cu9.ari.gaiaorbit.event.EventManager;
import gaia.cu9.ari.gaiaorbit.event.Events;
import gaia.cu9.ari.gaiaorbit.event.IObserver;
import gaia.cu9.ari.gaiaorbit.scenegraph.CameraManager.CameraMode;
import gaia.cu9.ari.gaiaorbit.scenegraph.NaturalCamera;
import gaia.cu9.ari.gaiaorbit.util.GlobalConf;

public class NaturalControllerListener implements ControllerListener, IObserver {

    NaturalCamera cam;
    IControllerMappings mappings;

    public NaturalControllerListener(NaturalCamera cam, String mappingsFile) {
	this.cam = cam;
	updateControllerMappings(mappingsFile);
	EventManager.instance.subscribe(this, Events.RELOAD_CONTROLLER_MAPPINGS);
    }

    public boolean updateControllerMappings(String mappingsFile) {
	if (Gdx.files.absolute(mappingsFile).exists())
	    mappings = new ControllerMappings(Gdx.files.absolute(mappingsFile));
	else {
	    // Defaults to xbox360
	    mappings = new XBox360Mappings();
	}
	return false;
    }

    @Override
    public void connected(Controller controller) {
	EventManager.instance.post(Events.POST_NOTIFICATION, this.getClass().getSimpleName(),
		"Controller connected: " + controller.getName());
    }

    @Override
    public void disconnected(Controller controller) {
	EventManager.instance.post(Events.POST_NOTIFICATION, this.getClass().getSimpleName(),
		"Controller disconnected: " + controller.getName());
    }

    @Override
    public boolean buttonDown(Controller controller, int buttonCode) {
	if (buttonCode == mappings.getButtonVelocityMultiplierHalf()) {
	    cam.setGamepadMultiplier(0.5);
	} else if (buttonCode == mappings.getButtonVelocityMultiplierTenth()) {
	    cam.setGamepadMultiplier(0.1);
	} else if (buttonCode == mappings.getButtonVelocityUp()) {
	    cam.setVelocity(1);
	} else if (buttonCode == mappings.getButtonVelocityDown()) {
	    cam.setVelocity(-1);
	}
	cam.setInputByController(true);
	return true;
    }

    @Override
    public boolean buttonUp(Controller controller, int buttonCode) {
	if (buttonCode == mappings.getButtonVelocityMultiplierHalf()) {
	    cam.setGamepadMultiplier(1);
	} else if (buttonCode == mappings.getButtonVelocityMultiplierTenth()) {
	    cam.setGamepadMultiplier(1);
	} else if (buttonCode == mappings.getButtonVelocityUp()) {
	    cam.setVelocity(0);
	} else if (buttonCode == mappings.getButtonVelocityDown()) {
	    cam.setVelocity(0);
	}
	cam.setInputByController(true);
	return true;
    }

    @Override
    public boolean axisMoved(Controller controller, int axisCode, float value) {
	boolean treated = false;

	// y = x^4
	// http://www.wolframalpha.com/input/?i=y+%3D+sign%28x%29+*+x%5E2+%28x+from+-1+to+1%29}
	value = Math.signum(value) * value * value * value * value;

	if (axisCode == mappings.getAxisRoll()) {
	    if (cam.getMode().equals(CameraMode.Focus)) {
		cam.setRoll(value * 1e-2f);
	    } else {
		// Use this for lateral movement
		cam.setHorizontalRotation(value);
	    }
	    treated = true;
	} else if (axisCode == mappings.getAxisPitch()) {
	    if (cam.getMode().equals(CameraMode.Focus)) {
		cam.setVerticalRotation(value * 0.1);
	    } else {
		cam.setPitch((GlobalConf.controls.INVERT_LOOK_Y_AXIS ? 1 : -1) * value * 1.5e-2f);
	    }
	    treated = true;
	} else if (axisCode == mappings.getAxisYaw()) {
	    if (cam.getMode().equals(CameraMode.Focus)) {
		cam.setHorizontalRotation(value * 0.1);
	    } else {
		cam.setYaw(value * 1.5e-2f);
	    }
	    treated = true;
	} else if (axisCode == mappings.getAxisMove()) {
	    if (Math.abs(value) < 0.005)
		value = 0;
	    cam.setVelocity(-value);
	    treated = true;
	} else if (axisCode == mappings.getAxisVelocityUp()) {
	    cam.setVelocity((value + 1f) / 2.0f);
	    treated = true;
	} else if (axisCode == mappings.getAxisVelocityDown()) {
	    cam.setVelocity(-(value + 1f) / 2.0f);
	    treated = true;
	}

	if (treated)
	    cam.setInputByController(true);

	return treated;
    }

    @Override
    public boolean povMoved(Controller controller, int povCode, PovDirection value) {
	return false;
    }

    @Override
    public boolean xSliderMoved(Controller controller, int sliderCode, boolean value) {
	return false;
    }

    @Override
    public boolean ySliderMoved(Controller controller, int sliderCode, boolean value) {
	return false;
    }

    @Override
    public boolean accelerometerMoved(Controller controller, int accelerometerCode, Vector3 value) {
	return false;
    }

    @Override
    public void notify(Events event, Object... data) {
	switch (event) {
	case RELOAD_CONTROLLER_MAPPINGS:
	    updateControllerMappings((String) data[0]);
	    break;
	default:
	    break;
	}

    }

}
