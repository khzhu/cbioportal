/** Copyright (c) 2012 Memorial Sloan-Kettering Cancer Center.
 **
 ** This library is free software; you can redistribute it and/or modify it
 ** under the terms of the GNU Lesser General Public License as published
 ** by the Free Software Foundation; either version 2.1 of the License, or
 ** any later version.
 **
 ** This library is distributed in the hope that it will be useful, but
 ** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 ** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 ** documentation provided hereunder is on an "as is" basis, and
 ** Memorial Sloan-Kettering Cancer Center
 ** has no obligations to provide maintenance, support,
 ** updates, enhancements or modifications.  In no event shall
 ** Memorial Sloan-Kettering Cancer Center
 ** be liable to any party for direct, indirect, special,
 ** incidental or consequential damages, including lost profits, arising
 ** out of the use of this software and its documentation, even if
 ** Memorial Sloan-Kettering Cancer Center
 ** has been advised of the possibility of such damage.  See
 ** the GNU Lesser General Public License for more details.
 **
 ** You should have received a copy of the GNU Lesser General Public License
 ** along with this library; if not, write to the Free Software Foundation,
 ** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 **/

package org.mskcc.cbio.cgds.dao;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mskcc.cbio.cgds.model.CancerStudy;
import org.mskcc.cbio.cgds.model.ClinicalData;
import org.mskcc.cbio.cgds.model.ClinicalAttribute;
import org.mskcc.cbio.cgds.model.Patient;
import org.mskcc.cbio.cgds.model.ClinicalParameterMap;

import java.sql.*;
import java.util.*;

/**
 * Data Access Object for `clinical` table
 *
 * @author Gideon Dresdner dresdnerg@cbio.mskcc.org
 */
public final class DaoClinicalData {

    private static Log log = LogFactory.getLog(DaoClinicalData.class);

    /**
     * add a new clinical datum
     *
     * @param cancerStudyId
     * @param caseId
     * @param attrId
     * @param attrVal
     * @return number of rows added to the database
     */
    public static int addDatum(int cancerStudyId,
                        String caseId,
                        String attrId,
                        String attrVal) throws DaoException {
        if (MySQLbulkLoader.isBulkLoad()) {
            MySQLbulkLoader.getMySQLbulkLoader("clinical").insertRecord(
                    Integer.toString(cancerStudyId),
                    caseId,
                    attrId,
                    attrVal
                    );
            return 1;
        }
        
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = JdbcUtil.getDbConnection(DaoClinicalData.class);
            pstmt = con.prepareStatement
                    ("INSERT INTO clinical(" +
                            "`CANCER_STUDY_ID`," +
                            "`CASE_ID`," +
                            "`ATTR_ID`," +
                            "`ATTR_VALUE`)" +
                            " VALUES(?,?,?,?)");
            pstmt.setInt(1, cancerStudyId);
            pstmt.setString(2, caseId);
            pstmt.setString(3, attrId);
            pstmt.setString(4, attrVal);

            int rows = pstmt.executeUpdate();
            return rows;
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            JdbcUtil.closeAll(DaoClinicalData.class, con, pstmt, rs);
        }
    }

    public static ClinicalData getDatum(String cancerStudyId, String caseId, String attrId) throws DaoException {
        CancerStudy cancerStudy = DaoCancerStudy.getCancerStudyByStableId(cancerStudyId);

        return DaoClinicalData.getDatum(cancerStudy.getInternalId(), caseId, attrId);
    }

    public static ClinicalData getDatum(int cancerStudyId, String caseId, String attrId)
            throws DaoException {

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            con = JdbcUtil.getDbConnection(DaoClinicalData.class);

            pstmt = con.prepareStatement("SELECT * FROM clinical WHERE " +
                    "CANCER_STUDY_ID=? " +
                    "AND CASE_ID=? " +
                    "AND ATTR_ID=?");

            pstmt.setInt(1, cancerStudyId);
            pstmt.setString(2, caseId);
            pstmt.setString(3, attrId);

            rs = pstmt.executeQuery();

            if (rs.next()) {
                return extract(rs);
            } else {
                throw new DaoException(String.format("clincial not found for (%d, %s, %s)",
                        cancerStudyId, caseId, attrId));
            }

        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            JdbcUtil.closeAll(DaoClinicalData.class, con, pstmt, rs);
        }
    }

    public static List<ClinicalData> getDataByCaseId(int cancerStudyId, String caseId)
            throws DaoException {

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

		List<ClinicalData> clinicals = new ArrayList<ClinicalData>();

        try {
            con = JdbcUtil.getDbConnection(DaoClinicalData.class);

            pstmt = con.prepareStatement("SELECT * FROM clinical WHERE " +
										 "CANCER_STUDY_ID=? " +
										 "AND CASE_ID=? ");

            pstmt.setInt(1, cancerStudyId);
            pstmt.setString(2, caseId);

            rs = pstmt.executeQuery();

            while (rs.next()) {
                clinicals.add(extract(rs));
			}
		}
		catch (SQLException e) {
			throw new DaoException(String.format("clincial not found for (%d, %s, %s)",
												 cancerStudyId, caseId));
		}
		finally {
			JdbcUtil.closeAll(DaoClinicalData.class, con, pstmt, rs);
		}

		return clinicals;
	}

    /**
     * Query by cancer_study_id
     *
     * Looks up the corresponding <code>CancerStudy</code> object to get the database id
     *
     * @param cancerStudyId     String
     * @return
     * @throws DaoException
     */
    public static List<ClinicalData> getData(String cancerStudyId) throws DaoException {
        CancerStudy cancerStudy = DaoCancerStudy.getCancerStudyByStableId(cancerStudyId);

        return DaoClinicalData.getData(cancerStudy.getInternalId());
	}

    public static List<ClinicalData> getData(int cancerStudyId) throws DaoException {

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        List<ClinicalData> clinicals = new ArrayList<ClinicalData>();

        try {
            con = JdbcUtil.getDbConnection(DaoClinicalData.class);
            pstmt = con.prepareStatement("SELECT * FROM clinical WHERE CANCER_STUDY_ID=?");
            pstmt.setInt(1, cancerStudyId);

           rs = pstmt.executeQuery();

            while(rs.next()) {
                clinicals.add(extract(rs));
            }
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return clinicals;
    }

    /**
     * Get data for a list of case ids, for a particular cancer study
     * @param cancerStudyId
     * @param caseIds
     * @return
     */
    public static List<ClinicalData> getData(String cancerStudyId, Collection<String> caseIds) throws DaoException {
        CancerStudy cancerStudy = DaoCancerStudy.getCancerStudyByStableId(cancerStudyId);

		return DaoClinicalData.getData(cancerStudy.getInternalId(), caseIds);
	}

    public static List<ClinicalData> getData(int cancerStudyId, Collection<String> caseIds) throws DaoException {

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        List<ClinicalData> clinicals = new ArrayList<ClinicalData>();

        String caseIdsSql = generateCaseIdsSql(caseIds);

        String sql = "SELECT * FROM clinical WHERE `CANCER_STUDY_ID`=" + cancerStudyId
                + " " + "AND `CASE_ID` IN (" + caseIdsSql + ")";

        try {
            con = JdbcUtil.getDbConnection(DaoClinicalData.class);
            pstmt = con.prepareStatement(sql);
            rs = pstmt.executeQuery();
            while(rs.next()) {
                clinicals.add(extract(rs));
            }
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return clinicals;
    }


    public static List<ClinicalData> getData(String cancerStudyId, Collection<String> caseIds, ClinicalAttribute attr) throws DaoException {
        CancerStudy cancerStudy = DaoCancerStudy.getCancerStudyByStableId(cancerStudyId);

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        List<ClinicalData> clinicals = new ArrayList<ClinicalData>();

        String caseIdsSql = generateCaseIdsSql(caseIds);

        String sql = "SELECT * FROM clinical WHERE"
                + "`CANCER_STUDY_ID`=" + "'" + cancerStudy.getInternalId() + "'"
                + " " + "AND `ATTR_ID`=" + "'" + attr.getAttrId() + "'"
                + " " + "AND `CASE_ID` IN (" + caseIdsSql + ")";

        try {
            con = JdbcUtil.getDbConnection(DaoClinicalData.class);
            pstmt = con.prepareStatement(sql);
            rs = pstmt.executeQuery();
            while(rs.next()) {
                clinicals.add(extract(rs));
            }
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return clinicals;
    }

    public static List<ClinicalData> getDataByAttributeId(int cancerStudyId, String attributeId) throws DaoException {

		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		List<ClinicalData> clinicals = new ArrayList<ClinicalData>();

		try {
            con = JdbcUtil.getDbConnection(DaoClinicalData.class);

            pstmt = con.prepareStatement("SELECT * FROM clinical WHERE " +
                    "CANCER_STUDY_ID=? " +
                    "AND ATTR_ID=? ");

            pstmt.setInt(1, cancerStudyId);
            pstmt.setString(2, attributeId);

            rs = pstmt.executeQuery();
            while(rs.next()) {
                clinicals.add(extract(rs));
            }
		}
		catch (SQLException e) {
			throw new DaoException(e);
        }
		finally {
            JdbcUtil.closeAll(DaoClinicalData.class, con, pstmt, rs);
        }

        return clinicals;
    }

    /**
     * Generates a comma separated string of caseIds
     *
     * @param caseIds
     * @return
     */
    private static String generateCaseIdsSql(Collection<String> caseIds) {
        String caseIdsSql = "'" + StringUtils.join(caseIds, "','") + "'";
        return caseIdsSql;
    }

    /**
     * Turns a result set into a <code>ClinicalData</code> object
     *
     * returns null on failure to extract
     *
     * @param rs
     * @return
     * @throws SQLException
     */
    private static ClinicalData extract(ResultSet rs) throws SQLException {
		return new ClinicalData(rs.getInt("CANCER_STUDY_ID"),
								rs.getString("CASE_ID"),
								rs.getString("ATTR_ID"),
								rs.getString("ATTR_VALUE"));
    }

    /**
     * Deletes all Records.
     * @throws DaoException DAO Error.
     */
    public static void deleteAllRecords() throws DaoException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = JdbcUtil.getDbConnection(DaoClinicalData.class);
            pstmt = con.prepareStatement("TRUNCATE TABLE clinical");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            JdbcUtil.closeAll(DaoClinicalData.class, con, pstmt, rs);
        }
    }

	/*********************************************************
	 * Previous DaoClinicalData class methods (accessors only)
	 *********************************************************/
	
	public static Patient getSurvivalData(int cancerStudyId, String _case)  throws DaoException {
            List<Patient> patients = getSurvivalData(cancerStudyId, Collections.singleton(_case));
            return patients.isEmpty() ? null : patients.get(0);
	}

	public static List<Patient> getSurvivalData(int cancerStudyId, Collection<String> caseSet) throws DaoException {
            List<ClinicalData> data = getData(cancerStudyId, caseSet);
            Map<String,Map<String,ClinicalData>> clinicalData = new LinkedHashMap<String,Map<String,ClinicalData>>();
            for (ClinicalData cd : data) {
                String caseId = cd.getCaseId();
                Map<String,ClinicalData> msc = clinicalData.get(cd.getCaseId());
                if (msc==null) {
                    msc = new HashMap<String,ClinicalData>();
                    clinicalData.put(caseId, msc);
                }
                msc.put(cd.getAttrId(), cd);
            }

            ArrayList<Patient> toReturn = new ArrayList<Patient>();
            for (Map.Entry<String,Map<String,ClinicalData>> entry : clinicalData.entrySet()) {
                toReturn.add(new Patient(entry.getKey(), entry.getValue()));
            }
            return toReturn;
	}

	/**************************************************************
	 * Previous DaoClinicalFreeForm class methods (accessors only)
	 *************************************************************/

	public static ClinicalParameterMap getDataSlice(int cancerStudyId, String attributeId) throws DaoException {
		
		if (cancerStudyId < 0 || attributeId == null || attributeId.length() == 0) {
			throw new IllegalArgumentException("Invalid cancer study id or attribute id: [" +
											   cancerStudyId + ", " + attributeId + "]");
		}

		HashMap<String, String> parameterMap = new HashMap<String,String>();
		for (ClinicalData clinical : DaoClinicalData.getDataByAttributeId(cancerStudyId, attributeId)) {
			String value = clinical.getAttrVal();
			if (value.length() > 0 && !value.equals(ClinicalAttribute.NA)) {
				parameterMap.put(clinical.getCaseId(), clinical.getAttrVal());
			}
		}

		return new ClinicalParameterMap(attributeId, parameterMap);
	}
	public static HashSet<String> getDistinctParameters(int cancerStudyId) throws DaoException {

		if (cancerStudyId < 0) {
			throw new IllegalArgumentException("Invalid cancer study id: [" + cancerStudyId + "]");
		}

		HashSet<String> toReturn = new HashSet<String>();
		for (ClinicalData clinicalData : DaoClinicalData.getData(cancerStudyId)) {
			toReturn.add(clinicalData.getAttrId());
		}

		return toReturn;
	}
	public static HashSet<String> getAllCases (int cancerStudyId) throws DaoException {

		if (cancerStudyId < 0) {
			throw new IllegalArgumentException("Invalid cancer study id: [" + cancerStudyId + "]");
		}

		HashSet<String> toReturn = new HashSet<String>();
		for (ClinicalData clinicalData : DaoClinicalData.getData(cancerStudyId)) {
			toReturn.add(clinicalData.getCaseId());
		}

		return toReturn;
	}
	public static List<ClinicalData> getCasesByCancerStudy(int cancerStudyId) throws DaoException {

		if (cancerStudyId < 0) {
			throw new IllegalArgumentException("Invalid cancer study id: [" + cancerStudyId + "]");
		}

		return DaoClinicalData.getData(cancerStudyId);
	}
	public static List<ClinicalData> getCasesById(int cancerStudyId, String caseId) throws DaoException {

		if (cancerStudyId < 0 || caseId == null || caseId.length() == 0) {
			throw new IllegalArgumentException("Invalid cancer study or case id: [" +
											   cancerStudyId + ", " + caseId + "]");
		}

		return DaoClinicalData.getDataByCaseId(cancerStudyId, caseId);
	}
	public static List<ClinicalData> getCasesByCases(int cancerStudyId, List<String> caseIds) throws DaoException {

		if (cancerStudyId < 0 || caseIds.isEmpty()) {
			throw new IllegalArgumentException("Invalid cancer study or case id set size: [" +
											   cancerStudyId + ", " + caseIds.size() + "]");
		}

		return DaoClinicalData.getData(cancerStudyId, caseIds);
	}
        
        public static List<String> getCaseIdsByAttribute(int cancerStudyId, String paramName, String paramValue) throws DaoException {
            Connection con = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;

            try{
                con = JdbcUtil.getDbConnection(DaoClinicalData.class);
                pstmt = con.prepareStatement ("SELECT CASE_ID FROM `clinical`"
                        + "WHERE CANCER_STUDY_ID=? AND ATTR_ID=? AND ATTR_VALUE=?");
                pstmt.setInt(1, cancerStudyId);
                pstmt.setString(2, paramName);
                pstmt.setString(3, paramValue);
                rs = pstmt.executeQuery();

                List<String> cases = new ArrayList<String>();

                while (rs.next())
                {
                    cases.add(rs.getString("CASE_ID"));
                }

                return cases;
            } catch (SQLException e) {
                throw new DaoException(e);
            } finally {
                JdbcUtil.closeAll(DaoClinicalData.class, con, pstmt, rs);
            }

        }
}
