package com.mkobos.pca_transform;

import Jama.Matrix;
import com.mkobos.pca_transform.covmatrixevd.*;

import android.graphics.Matrix;

/**
 * Created by Sans on 9/17/2017.
 */

public class PCA {
    public enum TransformationType { ROTATION, WHITENING };

    private final boolean centerMatrix;

    private final int inputDim;

    private final Matrix whiteningTransformation;
    private final Matrix pcaRotationTransformation;
    private final Matrix v;
    private final Matrix zerosRotationTransformation;
    private final Matrix d;
    private final double[] means;
    private final double threshold;
    public PCA(Matrix data){
        this(data, new SVDBased(), true);
    }

    public PCA(Matrix data, boolean center){
        this(data, new SVDBased(), center);
    }

    public PCA(Matrix data, CovarianceMatrixEVDCalculator evdCalc){
        this(data, evdCalc, true);
    }


    public PCA(Matrix data, CovarianceMatrixEVDCalculator evdCalc, boolean center){
        this.centerMatrix = center;
        this.inputDim = data.getColumnDimension();
        this.means = getColumnsMeans(data);

        Matrix centeredData = data;
        if(centerMatrix){
            centeredData = shiftColumns(data, means);
        }
        //debugWrite(centeredData, "centeredData.csv");

        EVDResult evd = evdCalc.run(centeredData);
        EVDWithThreshold evdT = new EVDWithThreshold(evd);
        this.d = evdT.getDAboveThreshold();
        this.v = evdT.getVAboveThreshold();
        this.zerosRotationTransformation = evdT.getVBelowThreshold();
        this.threshold = 3*evdT.getThreshold();

        //debugWrite(this.evd.v, "eigen-v.csv");
        //debugWrite(this.evd.d, "eigen-d.csv");

        Matrix sqrtD = sqrtDiagonalMatrix(d);
        Matrix scaling = inverseDiagonalMatrix(sqrtD);
        //debugWrite(scaling, "scaling.csv");
        this.pcaRotationTransformation = v;
        this.whiteningTransformation =
                this.pcaRotationTransformation.times(scaling);
    }

    public Matrix getEigenvectorsMatrix(){
        return v;
    }

    public double getEigenvalue(int dimNo){
        return d.get(dimNo, dimNo);
    }

    public int getInputDimsNo(){
        return inputDim;
    }

    public int getOutputDimsNo(){
        return v.getColumnDimension();
    }

    public Matrix transform(Matrix data, TransformationType type){
        Matrix centeredData = data;
        if(centerMatrix){
            centeredData = shiftColumns(data, means);
        }
        Matrix transformation = getTransformation(type);
        return centeredData.times(transformation);
    }

    public boolean belongsToGeneratedSubspace(Matrix pt){
        Assume.assume(pt.getRowDimension()==1);
        Matrix centeredPt = pt;
        if(centerMatrix){
            centeredPt = shiftColumns(pt, means);
        }
        Matrix zerosTransformedPt = centeredPt.times(zerosRotationTransformation);
        assert zerosTransformedPt.getRowDimension()==1;
        for(int c = 0;                    c< zerosTransformedPt.getColumnDimension(); c++)
            if(Math.abs(zerosTransformedPt.get(0, c)) > threshold) {
                return false;
            }
        return true;
    }

    protected static Matrix calculateCovarianceMatrix(Matrix data){
        double[] means = getColumnsMeans(data);
        Matrix centeredData = shiftColumns(data, means);
        return EVDBased.calculateCovarianceMatrixOfCenteredData(
                centeredData);
    }

    private Matrix getTransformation(TransformationType type){
        switch(type){
            case ROTATION: return pcaRotationTransformation;
            case WHITENING: return  whiteningTransformation;
            default: throw new RuntimeException("Unknown enum type: "+type);
        }
    }

    private static Matrix shiftColumns(Matrix data, double[] shifts){
        Assume.assume(shifts.length==data.getColumnDimension());
        Matrix m = new Matrix(
                data.getRowDimension(), data.getColumnDimension());
        for(int c = 0; c < data.getColumnDimension(); c++)
            for(int r = 0; r < data.getRowDimension(); r++)
                m.set(r, c, data.get(r, c) - shifts[c]);
        return m;
    }

    private static double[] getColumnsMeans(Matrix m){
        double[] means = new double[m.getColumnDimension()];
        for(int c = 0; c < m.getColumnDimension(); c++){
            double sum = 0;
            for(int r = 0; r < m.getRowDimension(); r++)
                sum += m.get(r, c);
            means[c] = sum/m.getRowDimension();
        }
        return means;
    }

    private static Matrix sqrtDiagonalMatrix(Matrix m){
        assert m.getRowDimension()==m.getColumnDimension();
        Matrix newM = new Matrix(m.getRowDimension(), m.getRowDimension());
        for(int i = 0; i < m.getRowDimension(); i++)
            newM.set(i, i, Math.sqrt(m.get(i, i)));
        return newM;
    }

    private static Matrix inverseDiagonalMatrix(Matrix m){
        assert m.getRowDimension()==m.getColumnDimension();
        Matrix newM = new Matrix(m.getRowDimension(), m.getRowDimension());
        for(int i = 0; i < m.getRowDimension(); i++)
            newM.set(i, i, 1/m.get(i, i));
        return newM;
    }

}

class EVDWithThreshold {
    public static final double precision = 2.220446e-16;
    private final EVDResult evd;
    private final double threshold;
    public EVDWithThreshold(EVDResult evd){
        this(evd, Math.sqrt(precision));
    }
    public EVDWithThreshold(EVDResult evd, double tol){
        this.evd = evd;
        this.threshold = firstComponentSD(evd)*tol;
    }

    private static double firstComponentSD(EVDResult evd){
        return Math.sqrt(evd.d.get(0, 0));
    }

    public double getThreshold(){
        return threshold;
    }

    public Matrix getDAboveThreshold(){
        int aboveThresholdElemsNo = getElementsNoAboveThreshold();
        Matrix newD = evd.d.getMatrix(0, aboveThresholdElemsNo-1,
                0, aboveThresholdElemsNo-1);
        return newD;
    }

    public Matrix getVAboveThreshold(){
        return evd.v.getMatrix(0, evd.v.getRowDimension()-1,
                0, getElementsNoAboveThreshold()-1);
    }

    public Matrix getVBelowThreshold(){
        return evd.v.getMatrix(0, evd.v.getRowDimension()-1,
                getElementsNoAboveThreshold(), evd.v.getColumnDimension()-1);
    }

    private int getElementsNoAboveThreshold(){
        for(int i = 0; i < evd.d.getColumnDimension(); i++){
            double val = Math.sqrt(evd.d.get(i, i));
            if(!(val > threshold)) return i;
        }
        return evd.d.getColumnDimension();
    }
}