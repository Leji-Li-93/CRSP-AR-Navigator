package com.example.lljsm.mymapproject;

import android.content.Context;

import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.maps.model.LatLng;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class ARHelper {

    public final static double EARTH_RADIUS = 6378100.0;

    /**
     * place a renderable object to a specify pose
     * @param session the session of the ar scene
     * @param parent the ar scene
     * @param renderable the object that need to be placed to the scene
     * @param pose the position that the object should be placed
     * @return the AnchorNode object of this renderable and pose
     */
    public static AnchorNode placeRenderableToPose(Session session, Scene parent, ModelRenderable renderable, Pose pose){
        AnchorNode anchorNode = null;
        if(session == null || parent == null || renderable == null || pose == null){
            return null;
        }
        anchorNode = new AnchorNode(session.createAnchor(pose));
        anchorNode.setRenderable(renderable);
        anchorNode.setParent(parent);
        return anchorNode;
    }

    /**
     * using the start geo-position, end geo-position, start ar camera pose and end ar camera pose to calculate
     * the rotation of the geo coordinate system and ar coordinate system
     * @param startPosition the start geo-position of the user
     * @param endPosition the end geo-position of the user
     * @param startPose the ar camera pose corresponding to the startPosition
     * @param endPose the ar camera pose corresponding to the endPosition
     * @return the float type array, 0 place is the cos, 1 place is sin
     */
    public static BigDecimal[] calculatePosition(LatLng startPosition, LatLng endPosition, Pose startPose, Pose endPose){
        BigDecimal[] matrix = new BigDecimal[2];

        BigDecimal dEast = changeInEast(startPosition, endPosition);
        BigDecimal dNorth = changeInNorth(startPosition, endPosition);

        BigDecimal dNZ = new BigDecimal(-(endPose.tz() - startPose.tz()));
        BigDecimal dX = new BigDecimal(endPose.tx() - startPose.tx());
        //BigDecimal dLng = new BigDecimal(endPosition.lng - startPosition.lng);
        //BigDecimal dLat = new BigDecimal(endPosition.lat - startPosition.lat);

        BigDecimal base = (dNorth.multiply(dNorth)).add((dEast.multiply(dEast)));
        matrix[0] = ((dNZ.multiply(dNorth)).add(dX.multiply(dEast))).divide(base, RoundingMode.HALF_UP);
        matrix[1] = ( (dNZ.multiply(dEast)).subtract(dX.multiply(dNorth)) ).divide(base, RoundingMode.HALF_UP);

        return matrix;
    }

    /**
     *
     * @param cos
     * @param sin
     * @param startPosition
     * @param endPosition
     * @param startPose
     * @return
     */
    public static Pose getTranslationPose(BigDecimal cos, BigDecimal sin, LatLng startPosition, LatLng endPosition, Pose startPose){
        //BigDecimal dLat = new BigDecimal(endPosition.lat - startPosition.lat);
        //BigDecimal dLng = new BigDecimal(endPosition.lng - startPosition.lng);

        BigDecimal dEast = changeInEast(startPosition, endPosition);
        BigDecimal dNorth = changeInNorth(startPosition, endPosition);

        BigDecimal dNZ = dEast.multiply(sin).add(dNorth.multiply(cos));
        BigDecimal dX = dEast.multiply(cos).subtract(dNorth.multiply(sin));

        float[] translation = startPose.getTranslation();
        translation[0] += dX.floatValue();
        translation[2] -= dNZ.floatValue();

        Pose result = Pose.makeTranslation(translation);
        //Pose result = new Pose(translation, startPose.getRotationQuaternion());
        return result;
    }

    public static BigDecimal changeInNorth(LatLng startPosition, LatLng endPosition){
        return new BigDecimal(Math.toRadians(endPosition.lat - startPosition.lat))
                .multiply(new BigDecimal(EARTH_RADIUS * 2 * Math.PI));
    }

    public static BigDecimal changeInEast(LatLng startPosition, LatLng endPosition){
        return new BigDecimal(Math.cos(Math.toRadians((endPosition.lat + startPosition.lat) / 2)))
                .multiply(new BigDecimal(EARTH_RADIUS * 2 * Math.PI))
                .multiply(new BigDecimal(Math.toRadians(endPosition.lng - startPosition.lng)));
    }

    /**
     *
     * @param arFragment
     * @return
     */
    public static Pose getPosefromPlanes(ArFragment arFragment){
        Collection<Plane> planeCollection = arFragment.getArSceneView().getArFrame().getUpdatedTrackables(Plane.class);
        if(planeCollection.isEmpty()){
            return null;
        }

        Iterator<Plane> planeIterator = planeCollection.iterator();
        Plane plane = null;

        while(planeIterator.hasNext()){
            plane = planeIterator.next();
        }

        if (plane == null){
            return null;
        } else {
            return plane.getCenterPose();
        }
    }

    public static Pose getCameraPose(ArFragment arFragment){
        return arFragment.getArSceneView().getArFrame().getCamera().getPose();
    }

    public static AnchorNode LineBetweenTwoAnchorNode(Context context, Session session, Scene scene, AnchorNode from, AnchorNode to){
        Quaternion quaternion = scene.getCamera().getWorldRotation();
        float[] translation = to.getAnchor().getPose().getTranslation();
        float[] rotation = {quaternion.x, quaternion.y, quaternion.z, quaternion.w};
        Pose linePose = new Pose(translation, rotation);
        AnchorNode lineNode = new AnchorNode(session.createAnchor(linePose));
        lineNode.setParent(scene);


        Vector3 vectorTo = new Vector3(translation[0], translation[1], translation[2]);
        translation = from.getAnchor().getPose().getTranslation();
        Vector3 vectorFrom = new Vector3(translation[0], translation[1], translation[2]);
        float lineLength = Vector3.subtract(vectorFrom, vectorTo).length();
        Vector3 lineVector = new Vector3(0, lineLength/2, 0);
        Color lineColor = new Color(android.graphics.Color.parseColor("#ffffff"));

        MaterialFactory.makeOpaqueWithColor(context, lineColor)
                .thenAccept(material -> {
                    ModelRenderable line = ShapeFactory.makeCylinder(0.002f, lineLength, lineVector, material);
                    line.setShadowReceiver(false);
                    line.setShadowCaster(false);

                    Vector3 directionFromTopToBottom = Vector3.subtract(vectorTo, vectorFrom).normalized();
                    Quaternion rotationFromTopToBottom = Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());

                    Node node = new Node();
                    node.setRenderable(line);
                    node.setParent(scene);
                    node.setWorldRotation(Quaternion.multiply(rotationFromTopToBottom,
                            Quaternion.axisAngle(new Vector3(1, 0, 0), 90)));
                });
        return lineNode;
    }

    public static ArrayList<AnchorNode> getDirectionLines(Context context, Session session, Scene scene, ArrayList<AnchorNode> nodes){
        ArrayList<AnchorNode> lines = new ArrayList<AnchorNode>(nodes.size() - 1);
        int length = nodes.size();
        for(int i = 1; i < length; i++){
            AnchorNode temp = LineBetweenTwoAnchorNode(context, session, scene, nodes.get(i - 1), nodes.get(i));
            lines.add(temp);
        }
        return lines;
    }
}
