/**
 * Copyright MaDgIK Group 2010 - 2015.
 */
package madgik.exareme.master.queryProcessor.decomposer.query;

import madgik.exareme.master.queryProcessor.decomposer.DecomposerUtils;
import madgik.exareme.master.queryProcessor.decomposer.dag.Node;
import madgik.exareme.master.queryProcessor.decomposer.dag.ResultList;
import madgik.exareme.master.queryProcessor.decomposer.federation.DBInfoReaderDB;
import madgik.exareme.master.queryProcessor.decomposer.federation.NamesToAliases;
import madgik.exareme.master.queryProcessor.decomposer.util.Util;

import org.apache.log4j.Logger;

import com.google.common.hash.HashCode;

import java.util.*;

/**
 * @author heraldkllapi
 */
public class SQLQuery {

	private static final Logger log = Logger.getLogger(SQLQuery.class);

	private List<Output> outputs;
	private List<Table> inputTables;
	// private boolean isNested;
	// public final List<Filter> filters = new ArrayList<>();
	// public final List<Join> joins = new ArrayList<>();
	private List<UnaryWhereCondition> unaryWhereConditions;
	private List<NonUnaryWhereCondition> binaryWhereConditions;
	private List<Column> groupBy = new ArrayList<Column>();
	private List<ColumnOrderBy> orderBy = new ArrayList<ColumnOrderBy>();
	private List<SQLQuery> unionqueries = new ArrayList<SQLQuery>();
	private HashMap<SQLQuery, String> nestedSelectSubqueries;
	private String temporaryTableName;
	private boolean selectAll;
	private boolean temporary;
	// in remote DB
	private boolean outputColumnsDinstict;
	// public final List<String> dbs = new ArrayList<String>();
	// public final HashMap<String, DB> dbs = new HashMap<String, DB>();
	// public DBInfo dbInfo;
	private int tableToSplit;
	private int limit;
	private boolean isUnionAll;
	private String unionAlias;
	// private String nestedSelectSubqueryAlias;
	private boolean hasUnionRootNode;
	private SQLQuery leftJoinTable;
	private SQLQuery rightJoinTable;
	private String joinType;
	private String rightJoinTableAlias;
	private String leftJoinTableAlias;
	private boolean isBaseTable;
	private boolean materialised;
	private Node nestedNode;
	private HashCode hashId = null;

	private boolean existsInCache;
	private boolean isDrop;

	private Node joinNode;

	private List<Operand> joinOperands;

	private String sql;
	private boolean isStringSQL;
	private boolean isCreateIndex;
	private String stringOutputs;

	public SQLQuery() {
		super();
		temporaryTableName = "table" + Util.createUniqueId();
		selectAll = false;
		temporary = true;
		outputColumnsDinstict = false;
		isUnionAll = true;
		hasUnionRootNode = false;
		isStringSQL = false;
		isBaseTable = false;
		unaryWhereConditions = new ArrayList<UnaryWhereCondition>();
		outputs = new ArrayList<Output>();
		inputTables = new ArrayList<Table>();
		binaryWhereConditions = new ArrayList<NonUnaryWhereCondition>();
		nestedSelectSubqueries = new HashMap<SQLQuery, String>();
		limit = -1;
		materialised = false;
		nestedNode = null;
		existsInCache = false;
		joinNode = null;
		joinOperands = new ArrayList<Operand>();
		tableToSplit = -1;
	}

	public String toDistSQL() {

		StringBuilder output = new StringBuilder();

		if (this.isCreateIndex) {
			output.append(sql);
			output.append(";");
			return output.toString();
		}

		this.convertUDFs();

		// Print project columns
		output.append("distributed create");
		if (this.isTemporary()) {
			output.append(" temporary");
		}
		output.append(" table ");
		output.append("\n");
		output.append(this.getTemporaryTableName());

		if (this.isStringSQL) {
			output.append(" as ");

			output.append(sql);
			output.append(";");
			return output.toString();
		}

		output.append(" \n");
		output.append("as ");

		output.append("direct ");

		output.append("\n");
		output.append(toSQL());
		return output.toString();
	}

	public String toSQL() {
		StringBuilder output = new StringBuilder();
		String separator = "";

		// if (!this.isHasUnionRootNode()) {
		output.append("select ");
		// }
		separator = "";
		if (this.isSelectAll() || this.getOutputs().isEmpty()) {
			output.append("*");
		} else {
			if (this.isOutputColumnsDinstict()) {
				output.append("distinct ");
			}
			for (Output c : getOutputs()) {
				output.append(separator);
				separator = ", \n";
				output.append(c.toString());
			}
			/*
			 * for (Function f : outputFunctions) { output.append(separator);
			 * separator = ", "; output.append(f.toString()); }
			 */
		}
		separator = "";
		// if (!this.isHasUnionRootNode()) {
		output.append(" from \n");
		// }
		if (this.getJoinType() != null) {

			for (int tableNo = 0; tableNo < this.inputTables.size() - 1; tableNo++) {
				output.append("(");

				output.append(inputTables.get(tableNo).toString().toLowerCase());

				output.append(" ");
				output.append(getJoinType());
				output.append(" ");
			}

			output.append(inputTables.get(this.inputTables.size() - 1).toString().toLowerCase());

			output.append(" on ");
			separator = "";
			for (int joinOp = joinOperands.size() - 1; joinOp > -1; joinOp--) {
				output.append(separator);
				output.append(joinOperands.get(joinOp).toString());
				separator = " and ";
				// output.append(")");

			}
			output.append(")");
			/*
			 * output.append(this.getLeftJoinTable().getResultTableName()); if
			 * (this.getLeftJoinTableAlias() != null) { output.append(" as ");
			 * output.append(getLeftJoinTableAlias()); }
			 * output.append(inputTables.get(0)); output.append(" ");
			 * output.append(getJoinType()); output.append(" ");
			 * output.append(inputTables.get(1));
			 * output.append(this.getRightJoinTable().getResultTableName()); if
			 * (this.getRightJoinTableAlias() != null) { output.append(" as ");
			 * output.append(getRightJoinTableAlias()); } output.append(" on ");
			 * output.append(joinOperand.toString());
			 */

		} else if (!this.unionqueries.isEmpty()) {
			// UNIONS
			output.append("(");
			for (int i = 0; i < this.getUnionqueries().size(); i++) {
				if (i == DecomposerUtils.MAX_NUMBER_OF_UNIONS) {
					break;
				}
				output.append(separator);
				output.append("select ");
				if (this.getUnionqueries().get(i).isOutputColumnsDinstict()) {
					output.append("distinct ");
				}
				output.append("* from \n");
				output.append(this.getUnionqueries().get(i).getResultTableName());
				if (this.isUnionAll()) {
					separator = " union all \n";
				} else {
					separator = " union ";
				}
			}
			output.append(")");
			if (getUnionAlias() != null) {
				output.append(" ");
				output.append(getUnionAlias());
			}

		} else {
			if (!this.nestedSelectSubqueries.isEmpty()) {
				// nested select subqueries
				for (SQLQuery nested : getNestedSelectSubqueries().keySet()) {
					String alias = this.nestedSelectSubqueries.get(nested);
					output.append(separator);
					output.append("(select ");
					if (nested.isOutputColumnsDinstict()) {
						output.append("distinct ");
					}
					output.append("* from \n");
					output.append(nested.getResultTableName());
					output.append(")");
					// if (nestedSelectSubqueryAlias != null) {
					output.append(" ");
					output.append(alias);
					separator = ", \n";
				} // }
			} // else {

			String joinKeyword = " JOIN \n";
			if (DecomposerUtils.USE_CROSS_JOIN) {
				joinKeyword = " CROSS JOIN \n";

				for (Table t : getInputTables()) {
					output.append(separator);

					output.append(t.toString().toLowerCase());

					separator = joinKeyword;
				}

			}
		}
		separator = "";

		for (NonUnaryWhereCondition wc : getBinaryWhereConditions()) {
			output.append(separator);
			output.append(wc.toString());
			separator = " and \n";
		}
		for (UnaryWhereCondition wc : getUnaryWhereConditions()) {
			output.append(separator);
			output.append(wc.toString());
			separator = " and \n";
		}

		if (!groupBy.isEmpty()) {
			separator = "";
			output.append(" \ngroup by ");
			for (Column c : getGroupBy()) {
				output.append(separator);
				output.append(c.toString());
				separator = ", ";
			}
		}
		if (!orderBy.isEmpty()) {
			separator = "";
			output.append(" \norder by ");
			for (ColumnOrderBy c : getOrderBy()) {
				output.append(separator);
				output.append(c.toString());
				separator = ", ";
			}
		}

		output.append(";");
		return output.toString();
	}

	// void readDBInfo() {
	// dbInfo=DBInfoReader.read("./conf/dbinfo.properties");
	// }

	private void convertUDFs() {
		for (Output o : this.outputs) {
			if (o.getObject() instanceof Function) {
				Function f = (Function) o.getObject();
				if (f.getFunctionName().equalsIgnoreCase("to_char")) {
					if (f.getParameters().size() == 2
							&& f.getParameters().get(1).toString().toUpperCase().equals("'YYYY-MM-DD'")) {
						Function f2 = new Function();
						f2.setFunctionName("DATE");
						f2.addParameter(f.getParameters().get(0));
						o.setObject(f2);
					}
				}
			}
		}
	}

	public boolean hasTheSameDistSQL(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final SQLQuery other = (SQLQuery) obj;
		if (this == other) {
			return true;
		}

		if (this.outputs != other.outputs && (this.outputs == null || !this.outputs.equals(other.outputs))) {
			return false;
		}
		if (this.inputTables != other.inputTables
				&& (this.inputTables == null || !this.inputTables.equals(other.inputTables))) {
			return false;
		}
		if (this.unaryWhereConditions != other.unaryWhereConditions && (this.unaryWhereConditions == null
				|| !this.unaryWhereConditions.equals(other.unaryWhereConditions))) {
			return false;
		}
		if (this.binaryWhereConditions != other.binaryWhereConditions && (this.binaryWhereConditions == null
				|| !this.binaryWhereConditions.equals(other.binaryWhereConditions))) {
			return false;
		}
		if (this.groupBy != other.groupBy && (this.groupBy == null || !this.groupBy.equals(other.groupBy))) {
			return false;
		}
		if (this.orderBy != other.orderBy && (this.orderBy == null || !this.orderBy.equals(other.orderBy))) {
			return false;
		}
		if (this.unionqueries != other.unionqueries
				&& (this.unionqueries == null || !this.unionqueries.equals(other.unionqueries))) {
			return false;
		}
		if (this.nestedSelectSubqueries != other.nestedSelectSubqueries && (this.nestedSelectSubqueries == null
				|| !this.nestedSelectSubqueries.equals(other.nestedSelectSubqueries))) {
			return false;
		}
		if (this.selectAll != other.selectAll) {
			return false;
		}
		if (this.temporary != other.temporary) {
			return false;
		}

		if (this.outputColumnsDinstict != other.outputColumnsDinstict) {
			return false;
		}
		if (this.limit != other.limit) {
			return false;
		}

		if (this.isUnionAll != other.isUnionAll) {
			return false;
		}
		if ((this.unionAlias == null) ? (other.unionAlias != null) : !this.unionAlias.equals(other.unionAlias)) {
			return false;
		}
		if (this.hasUnionRootNode != other.hasUnionRootNode) {
			return false;
		}
		if (this.leftJoinTable != other.leftJoinTable
				&& (this.leftJoinTable == null || !this.leftJoinTable.equals(other.leftJoinTable))) {
			return false;
		}
		if (this.rightJoinTable != other.rightJoinTable
				&& (this.rightJoinTable == null || !this.rightJoinTable.equals(other.rightJoinTable))) {
			return false;
		}
		if ((this.joinType == null) ? (other.joinType != null) : !this.joinType.equals(other.joinType)) {
			return false;
		}
		if ((this.rightJoinTableAlias == null) ? (other.rightJoinTableAlias != null)
				: !this.rightJoinTableAlias.equals(other.rightJoinTableAlias)) {
			return false;
		}
		if ((this.leftJoinTableAlias == null) ? (other.leftJoinTableAlias != null)
				: !this.leftJoinTableAlias.equals(other.leftJoinTableAlias)) {
			return false;
		}
		if (this.isBaseTable != other.isBaseTable) {
			return false;
		}
		return true;
	}

	public void addOutputColumnIfNotExists(String tableAlias, String columnName) {
		boolean exists = false;
		Column c = new Column(tableAlias, columnName);
		for (Output otherColumn : this.getOutputs()) {
			if (otherColumn.getObject() instanceof Column) {
				if (((Column) otherColumn.getObject()).equals(c)) {
					exists = true;
					break;
				}
			}
		}
		if (!exists) {
			this.getOutputs().add(new Output(tableAlias + "_" + columnName, c));
		}
	}

	public String getResultTableName() {
		return this.getTemporaryTableName();
	}

	public void setResultTableName(String name) {
		this.setTemporaryTableName(name);
	}

	public boolean isSelectAll() {
		return this.selectAll;
	}

	public void setSelectAll(boolean b) {
		this.selectAll = b;
	}

	public boolean isTemporary() {
		return this.temporary;
	}

	public void setTemporary(boolean b) {
		this.temporary = b;
	}

	/* returns columns included in OutputColumns and OutputFunctions */
	public ArrayList<Column> getAllOutputColumns() {
		ArrayList<Column> result = new ArrayList<Column>();
		for (Output o : this.getOutputs()) {
			for (Column c : o.getObject().getAllColumnRefs()) {
				result.add(c);
			}
		}

		return result;
	}

	/*
	 * returns columns included in OutputColumns, OutputFunctions and Where
	 * Conditions
	 */
	public ArrayList<Column> getAllColumns() {
		ArrayList<Column> result = new ArrayList<Column>();
		for (Output o : this.getOutputs()) {
			for (Column c : o.getObject().getAllColumnRefs()) {
				result.add(c);
			}
		}
		for (NonUnaryWhereCondition wc : this.getBinaryWhereConditions()) {
			for (Column c : wc.getAllColumnRefs()) {
				result.add(c);
			}
		}
		for (UnaryWhereCondition wc : this.getUnaryWhereConditions()) {
			result.add(wc.getAllColumnRefs().get(0));
		}

		for (Operand o : joinOperands) {
			for (Column c : o.getAllColumnRefs()) {
				result.add(c);
			}
		}
		return result;
	}

	public ArrayList<Column> getWhereColumns() {
		ArrayList<Column> result = new ArrayList<Column>();
		for (NonUnaryWhereCondition wc : this.getBinaryWhereConditions()) {
			for (Column c : wc.getAllColumnRefs()) {
				result.add(c);
			}
		}
		for (UnaryWhereCondition wc : this.getUnaryWhereConditions()) {
			result.add(wc.getAllColumnRefs().get(0));
		}
		return result;
	}

	public List<String> getListOfJoinTables() {
		List<String> joinTables = new ArrayList<String>();
		for (Column c : this.getWhereColumns()) {
			if (!joinTables.contains(c.getAlias())) {
				joinTables.add(c.getAlias());
			}
		}
		return joinTables;
	}

	public void setOutputColumnsDistinct(boolean b) {
		this.setOutputColumnsDinstict(b);
	}

	public boolean getOutputColumnsDistinct() {
		return this.isOutputColumnsDinstict();
	}

	public void setUnionAll(boolean all) {
		this.setIsUnionAll(all);
	}

	public void setUnionAlias(String correlationName) {
		this.unionAlias = correlationName;
	}

	// public void setNestedSelectSubqueryAlias(String correlationName) {
	// this.nestedSelectSubqueryAlias = correlationName;
	// }
	public void setHasUnionRootNode(boolean b) {
		this.hasUnionRootNode = b;
	}

	public boolean hasNestedSuqueriesOrLeftJoin() {
		return this.getJoinType() != null || !this.unionqueries.isEmpty() || !this.nestedSelectSubqueries.isEmpty();
	}

	public boolean hasNestedSuqueries() {
		return !this.unionqueries.isEmpty() || !this.nestedSelectSubqueries.isEmpty();
	}

	public ArrayList<Column> getAllSubqueryColumns() {
		// returns all columns from these query and its subqueries
		ArrayList<Column> result = new ArrayList<Column>();
		for (Column c : this.getAllColumns()) {
			result.add(c);
		}
		if (!nestedSelectSubqueries.isEmpty()) {
			for (SQLQuery nested : getNestedSelectSubqueries().keySet()) {
				for (Column c : nested.getAllSubqueryColumns()) {
					result.add(c);
				}
			}
		}
		if (this.getLeftJoinTable() != null) {
			for (Column c : this.getLeftJoinTable().getAllSubqueryColumns()) {
				result.add(c);
			}
		}
		if (this.getRightJoinTable() != null) {
			for (Column c : this.getRightJoinTable().getAllSubqueryColumns()) {
				result.add(c);
			}
		}
		for (SQLQuery q : this.getUnionqueries()) {
			for (Column c : q.getAllSubqueryColumns()) {
				result.add(c);
			}
		}
		return result;
	}

	public void addNestedSelectSubquery(SQLQuery nested, String alias) {
		this.getNestedSelectSubqueries().put(nested, alias);
	}

	public Set<SQLQuery> getNestedSubqueries() {
		return this.getNestedSelectSubqueries().keySet();
	}

	public String getNestedSubqueryAlias(SQLQuery s) {
		return this.getNestedSelectSubqueries().get(s);
	}

	public List<String> getOutputAliases() {
		List<String> result = new ArrayList<String>();
		for (Output o : this.getOutputs()) {
			result.add(o.getOutputName());
		}
		return result;
	}

	public Set<String> getTableAliases() {
		Set<String> result = new HashSet<String>();
		for (Table t : this.inputTables) {
			result.add(t.getAlias());
		}
		return result;
	}

	/**
	 * @return the outputs
	 */
	public List<Output> getOutputs() {
		return outputs;
	}

	/**
	 * @param outputs
	 *            the outputs to set
	 */
	public void setOutputs(List<Output> outputs) {
		this.outputs = outputs;
	}

	/**
	 * @return the inputTables
	 */
	public List<Table> getInputTables() {
		return inputTables;
	}

	/**
	 * @param inputTables
	 *            the inputTables to set
	 */
	public void setInputTables(List<Table> inputTables) {
		this.inputTables = inputTables;
	}

	/**
	 * @return the unaryWhereConditions
	 */
	public List<UnaryWhereCondition> getUnaryWhereConditions() {
		return unaryWhereConditions;
	}

	/**
	 * @param unaryWhereConditions
	 *            the unaryWhereConditions to set
	 */
	public void setUnaryWhereConditions(List<UnaryWhereCondition> unaryWhereConditions) {
		this.unaryWhereConditions = unaryWhereConditions;
	}

	/**
	 * @return the binaryWhereConditions
	 */
	public List<NonUnaryWhereCondition> getBinaryWhereConditions() {
		return binaryWhereConditions;
	}

	/**
	 * @param binaryWhereConditions
	 *            the binaryWhereConditions to set
	 */
	public void setBinaryWhereConditions(List<NonUnaryWhereCondition> binaryWhereConditions) {
		this.binaryWhereConditions = binaryWhereConditions;
	}

	/**
	 * @return the groupBy
	 */
	public List<Column> getGroupBy() {
		return groupBy;
	}

	/**
	 * @param groupBy
	 *            the groupBy to set
	 */
	public void setGroupBy(List<Column> groupBy) {
		this.groupBy = groupBy;
	}

	/**
	 * @return the orderBy
	 */
	public List<ColumnOrderBy> getOrderBy() {
		return orderBy;
	}

	/**
	 * @param orderBy
	 *            the orderBy to set
	 */
	public void setOrderBy(List<ColumnOrderBy> orderBy) {
		this.orderBy = orderBy;
	}

	/**
	 * @return the unionqueries
	 */
	public List<SQLQuery> getUnionqueries() {
		return unionqueries;
	}

	/**
	 * @param unionqueries
	 *            the unionqueries to set
	 */
	public void setUnionqueries(List<SQLQuery> unionqueries) {
		this.unionqueries = unionqueries;
	}

	/**
	 * @return the nestedSelectSubqueries
	 */
	public HashMap<SQLQuery, String> getNestedSelectSubqueries() {
		return nestedSelectSubqueries;
	}

	/**
	 * @param nestedSelectSubqueries
	 *            the nestedSelectSubqueries to set
	 */
	public void setNestedSelectSubqueries(HashMap<SQLQuery, String> nestedSelectSubqueries) {
		this.nestedSelectSubqueries = nestedSelectSubqueries;
	}

	/**
	 * @return the temporaryTableName
	 */
	public String getTemporaryTableName() {
		return temporaryTableName;
	}

	/**
	 * @param temporaryTableName
	 *            the temporaryTableName to set
	 */
	public void setTemporaryTableName(String temporaryTableName) {
		this.temporaryTableName = temporaryTableName;
	}

	/**
	 * @return the outputColumnsDinstict
	 */
	public boolean isOutputColumnsDinstict() {
		return outputColumnsDinstict;
	}

	/**
	 * @param outputColumnsDinstict
	 *            the outputColumnsDinstict to set
	 */
	public void setOutputColumnsDinstict(boolean outputColumnsDinstict) {
		this.outputColumnsDinstict = outputColumnsDinstict;
	}

	/**
	 * @return the limit
	 */
	public int getLimit() {
		return limit;
	}

	/**
	 * @param limit
	 *            the limit to set
	 */
	public void setLimit(int limit) {
		this.limit = limit;
	}

	/**
	 * @return the isUnionAll
	 */
	public boolean isUnionAll() {
		return isUnionAll;
	}

	/**
	 * @param isUnionAll
	 *            the isUnionAll to set
	 */
	public void setIsUnionAll(boolean isUnionAll) {
		this.isUnionAll = isUnionAll;
	}

	/**
	 * @return the unionAlias
	 */
	public String getUnionAlias() {
		return unionAlias;
	}

	/**
	 * @return the hasUnionRootNode
	 */
	public boolean isHasUnionRootNode() {
		return hasUnionRootNode;
	}

	/**
	 * @return the leftJoinTable
	 */
	public SQLQuery getLeftJoinTable() {
		return leftJoinTable;
	}

	/**
	 * @param leftJoinTable
	 *            the leftJoinTable to set
	 */
	public void setLeftJoinTable(SQLQuery leftJoinTable) {
		this.leftJoinTable = leftJoinTable;
	}

	/**
	 * @return the rightJoinTable
	 */
	public SQLQuery getRightJoinTable() {
		return rightJoinTable;
	}

	/**
	 * @param rightJoinTable
	 *            the rightJoinTable to set
	 */
	public void setRightJoinTable(SQLQuery rightJoinTable) {
		this.rightJoinTable = rightJoinTable;
	}

	/**
	 * @return the joinType
	 */
	public String getJoinType() {
		return joinType;
	}

	/**
	 * @param joinType
	 *            the joinType to set
	 */
	public void setJoinType(String joinType) {
		this.joinType = joinType;
	}

	/**
	 * @return the rightJoinTableAlias
	 */
	public String getRightJoinTableAlias() {
		return rightJoinTableAlias;
	}

	/**
	 * @param rightJoinTableAlias
	 *            the rightJoinTableAlias to set
	 */
	public void setRightJoinTableAlias(String rightJoinTableAlias) {
		this.rightJoinTableAlias = rightJoinTableAlias;
	}

	/**
	 * @return the leftJoinTableAlias
	 */
	public String getLeftJoinTableAlias() {
		return leftJoinTableAlias;
	}

	/**
	 * @param leftJoinTableAlias
	 *            the leftJoinTableAlias to set
	 */
	public void setLeftJoinTableAlias(String leftJoinTableAlias) {
		this.leftJoinTableAlias = leftJoinTableAlias;
	}

	/**
	 * @return the isBaseTable
	 */
	public boolean isBaseTable() {
		return isBaseTable;
	}

	/**
	 * @param isBaseTable
	 *            the isBaseTable to set
	 */
	public void setIsBaseTable(boolean isBaseTable) {
		this.isBaseTable = isBaseTable;
	}

	public void normalizeWhereConditions() {
		List<Operand> toNormalize = new ArrayList<Operand>();
		toNormalize.addAll(this.getBinaryWhereConditions());
		List<List<Operand>> disjunctions = normalize(toNormalize);
		if (disjunctions.size() > 1) {
			ArrayList<SQLQuery> unions = new ArrayList<SQLQuery>();
			for (int i = 0; i < disjunctions.size(); i++) {
				List<Operand> cloned = new ArrayList<Operand>();
				try {
					for (Operand op : disjunctions.get(i)) {

						cloned.add(op.clone());
						// next.addBinaryWhereCondition((BinaryWhereCondition)bwc.clone());

					}
				} catch (CloneNotSupportedException ex) {
					log.error(ex.getMessage());
				}
				SQLQuery next = createNormalizedQueryForConditions(cloned, new SQLQuery());
				unions.add(next);
			}
			// this.setUnionAlias(this.getResultTableName());
			// this.setUnionAll(true);
			this.setUnionqueries(unions);
			this.setSelectAll(true);
			this.setBinaryWhereConditions(new ArrayList<NonUnaryWhereCondition>());
			this.setUnaryWhereConditions(new ArrayList<UnaryWhereCondition>());
			this.setNestedSelectSubqueries(new HashMap<SQLQuery, String>());
		}
	}

	/*
	 * takes a list of where conditions and "breaks" the disjunctions. for
	 * example if the input list contains a condition with an OR, it will return
	 * two lists of where conditions, each one containing one of the operators
	 * of OR and the rest of the initial conditions
	 */
	private List<List<Operand>> normalize(List<Operand> in) {
		List<List<Operand>> normalized = new ArrayList<List<Operand>>();
		// List<BinaryWhereCondition> firstDisjunction=new
		// ArrayList<BinaryWhereCondition>();
		List<Operand> clonedIn = new ArrayList<Operand>(in);
		normalized.add(clonedIn);
		for (int i = 0; i < in.size(); i++) {
			// for(BinaryWhereCondition bwc:in){
			Operand op = in.get(i);
			NonUnaryWhereCondition bwc = null;
			if (op instanceof NonUnaryWhereCondition) {
				bwc = (NonUnaryWhereCondition) op;

				if (bwc.getOperator().equals("or")) {

					if (!bwc.referencesAtMostOneTable()) {
						// if yes, leave as is, it will be pushed to a base
						// table

						for (List<Operand> tempResult : normalized) {
							tempResult.remove(bwc);
						}
						// in.remove(bwc);
						// i--;
						// List<Operand> second = new
						// ArrayList<Operand>(in);
						// normalized.add(second);
						List<List<Operand>> tempBeforeAddingLeftNested = new ArrayList<List<Operand>>(normalized);
						normalized.clear();
						Operand left = bwc.getLeftOp();
						Operand right = bwc.getRightOp();
						ArrayList<Operand> l = new ArrayList<Operand>();
						l.add(left);
						ArrayList<Operand> r = new ArrayList<Operand>();
						r.add(right);
						List<List<Operand>> nested = normalize(l);
						// normalized.remove(in);
						for (List<Operand> t : tempBeforeAddingLeftNested) {
							for (int j = 0; j < nested.size(); j++) {
								List<Operand> leftL = nested.get(j);

								List<Operand> nestedOr = new ArrayList<Operand>(t);
								normalized.add(nestedOr);
								for (Operand bl : leftL) {

									nestedOr.add(bl);
								}

							}
						}
						nested = normalize(r);
						for (List<Operand> t : tempBeforeAddingLeftNested) {
							// normalized.remove(second);
							for (int j = 0; j < nested.size(); j++) {
								List<Operand> rightR = nested.get(j);

								List<Operand> nestedOr = new ArrayList<Operand>(t);
								normalized.add(nestedOr);
								for (Operand br : rightR) {

									nestedOr.add(br);
								}
							}
						}
					}
					// in.add(left);
					// second.add(right);
					// break;
				} else if (bwc.getOperator().equals("and")) {
					in.remove(bwc);
					i--;
					Operand left = bwc.getLeftOp();
					Operand right = bwc.getRightOp();
					in.add(left);
					in.add(right);
					for (List<Operand> t : normalized) {
						t.remove(bwc);
						t.add(left);
						t.add(right);
					}
					/*
					 * ArrayList<NonUnaryWhereCondition> l = new
					 * ArrayList<NonUnaryWhereCondition>(); l.add(left);
					 * ArrayList<NonUnaryWhereCondition> r = new
					 * ArrayList<NonUnaryWhereCondition>(); r.add(right);
					 * List<List<NonUnaryWhereCondition>> nested = normalize(l);
					 * //normalized.remove(in); for (int j = 0; j <
					 * nested.size(); j++) { List<NonUnaryWhereCondition> leftL
					 * = nested.get(j);
					 * 
					 * // List<BinaryWhereCondition> nestedOr=new
					 * ArrayList<BinaryWhereCondition>(in); //
					 * normalized.add(nestedOr); for (NonUnaryWhereCondition bl
					 * : leftL) {
					 * 
					 * in.add(bl); }
					 * 
					 * } nested = normalize(r); for (int j = 0; j <
					 * nested.size(); j++) { List<NonUnaryWhereCondition> leftL
					 * = nested.get(j);
					 * 
					 * // List<BinaryWhereCondition> nestedOr=new
					 * ArrayList<BinaryWhereCondition>(in); //
					 * normalized.add(nestedOr); for (NonUnaryWhereCondition bl
					 * : leftL) {
					 * 
					 * in.add(bl); }
					 * 
					 * }
					 */

					/*
					 * for(List<BinaryWhereCondition> leftL:normalize(l)){
					 * for(BinaryWhereCondition bl:leftL){ in.add(bl); } }
					 * for(List<BinaryWhereCondition> rightL:normalize(r)){
					 * for(BinaryWhereCondition br:rightL){ in.add(br); } }
					 */
				}
			}
			// else{
			// if((bwc.getLeftOp() instanceof Column) && (bwc.getRightOp()
			// instanceof Column)){

			// }
			// }

		}
		// System.out.println(":::::::: " + normalized);
		return normalized;
	}

	public void addUnaryWhereCondition(UnaryWhereCondition uwc) {
		this.unaryWhereConditions.add(uwc);
	}

	public void addBinaryWhereCondition(NonUnaryWhereCondition uwc) {
		this.binaryWhereConditions.add(uwc);
	}

	/*
	 * to be used when normalizing queries. deep cloning of input tables and
	 * unary where conditions. Different table name. shallow cloning of outputs
	 */
	public SQLQuery createNormalizedQueryForConditions(List<Operand> conditions, SQLQuery normalized) {
		// SQLQuery normalized = new SQLQuery();
		for (Operand op : conditions) {
			if (op instanceof BinaryOperand) {
				BinaryOperand bo = (BinaryOperand) op;
				if (bo.getOperator().equalsIgnoreCase("and")) {
					List<Operand> nested = new ArrayList<Operand>();
					nested.add(bo.getLeftOp());
					nested.add(bo.getRightOp());
					createNormalizedQueryForConditions(nested, normalized);
					continue;
				}

				normalized.binaryWhereConditions
						.add(new NonUnaryWhereCondition(bo.getLeftOp(), bo.getRightOp(), bo.getOperator()));
			} else if (op instanceof NonUnaryWhereCondition) {
				normalized.binaryWhereConditions.add((NonUnaryWhereCondition) op);
			} else if (op instanceof UnaryWhereCondition) {
				normalized.unaryWhereConditions.add((UnaryWhereCondition) op);
			} else if (op instanceof Function) {
				Function f = (Function) op;
				NonUnaryWhereCondition nuwc = new NonUnaryWhereCondition();
				nuwc.setOperator(f.getFunctionName());
				for (Operand o : f.getParameters()) {
					nuwc.addOperand(o);
				}
				normalized.binaryWhereConditions.add(nuwc);
			} else {
				log.error("Unknown where condition type: " + op);
			}
		}
		try {
			ArrayList<Output> outs = new ArrayList<Output>();
			for (Output o : this.outputs) {

				outs.add(new Output(o.getOutputName(), o.getObject().clone()));

			}
			normalized.setOutputs(outs);
			normalized.inputTables = new ArrayList<Table>();
			for (Table t : this.inputTables) {
				normalized.inputTables.add(new Table(t.getName(), t.getAlias()));
			}
			// normalized.unaryWhereConditions = new
			// ArrayList<UnaryWhereCondition>();
			for (UnaryWhereCondition uwc : this.unaryWhereConditions) {
				normalized.addUnaryWhereCondition((UnaryWhereCondition) uwc.clone());
			}
			for (SQLQuery nested : this.nestedSelectSubqueries.keySet()) {
				List<NonUnaryWhereCondition> bwcs = new ArrayList<NonUnaryWhereCondition>();
				for (NonUnaryWhereCondition bwc : nested.getBinaryWhereConditions()) {
					bwcs.add((NonUnaryWhereCondition) bwc.clone());
				}
				boolean needed = false;
				for (Column c : normalized.getAllColumns()) {
					if (c.getAlias().equals(this.nestedSelectSubqueries.get(nested))) {
						needed = true;
						break;
					}
				}
				if (needed) {
					List<Operand> ops = new ArrayList<Operand>();
					for (NonUnaryWhereCondition nuwc : bwcs) {
						ops.add(nuwc);
					}
					normalized.nestedSelectSubqueries.put(
							nested.createNormalizedQueryForConditions(ops, new SQLQuery()),
							this.nestedSelectSubqueries.get(nested));
				}
			}
			normalized.setGroupBy(this.groupBy);
			normalized.setOrderBy(this.orderBy);
			normalized.setLimit(this.limit);
			normalized.selectAll = this.selectAll;
			normalized.temporary = this.temporary;
			normalized.outputColumnsDinstict = this.outputColumnsDinstict;

			normalized.isUnionAll = this.isUnionAll;
			normalized.unionAlias = this.unionAlias;
			normalized.hasUnionRootNode = this.hasUnionRootNode;
			// this.leftJoinTable = this.leftJoinTable;
			// this.rightJoinTable = this.rightJoinTable;
			// this.joinType = this.joinType;
			// this.rightJoinTableAlias = this.rightJoinTableAlias;
			// this.leftJoinTableAlias = this.leftJoinTableAlias;
			normalized.isBaseTable = this.isBaseTable;
			// remove input tables that we do not need (should be in other
			// unions)
		} catch (CloneNotSupportedException ex) {
			log.error(ex.getMessage());
		}
		if (normalized.getInputTables().size() > 1) {
			List<Table> toRemove = new ArrayList<Table>();
			for (Table t : normalized.getInputTables()) {
				boolean needed = false;

				for (Column c : normalized.getAllColumns()) {

					if (c.getAlias().equals(t.getAlias())) {
						needed = true;
						break;
					}
				}
				if (!needed) {
					toRemove.add(t);

				}
			}
			for (Table t : toRemove) {
				normalized.getInputTables().remove(t);
			}
		}

		return normalized;
	}

	public void addInputTableIfNotExists(Table table) {
		if (!this.inputTables.contains(table) && !table.getName().equals(this.getTemporaryTableName())) {
			this.inputTables.add(table);

		}
	}

	public List<List<String>> getListOfAliases(NamesToAliases n2a, boolean getOnlyFirst) {
		Map<String, Integer> counts = new HashMap<String, Integer>();
		List<List<String>> result = new ArrayList<List<String>>();
		if (this.inputTables.isEmpty()) {
			return result;
		}
		List<String> partialResult = new ArrayList<String>();
		for (Table t : this.inputTables) {
			// String localAlias = t.getAlias();
			// String globalAlias;
			if (counts.containsKey(t.getlocalName())) {
				counts.put(t.getlocalName(), counts.get(t.getlocalName()) + 1);
				n2a.getGlobalAliasForBaseTable(t.getlocalName(), counts.get(t.getlocalName()));
			} else {
				counts.put(t.getlocalName(), 0);
				n2a.getGlobalAliasForBaseTable(t.getlocalName(), 0);
			}
		}
		// for (int i=0;i<this.inputTables.size();i++) {
		// Table t=this.inputTables.get(i);

		// for(String alias:n2a.getAllAliasesForBaseTable(t.getlocalName())){
		// partialResult.add(alias);
		traverseTables(0, n2a, new ArrayList<String>(partialResult), result, getOnlyFirst);
		// System.out.println(result);
		// }
		// i++;
		// }
		return result;
	}

	public void traverseTables(int i, NamesToAliases n2a, List<String> partialResult, List<List<String>> result,
			boolean getOnlyFirst) {
		i++;
		Table t = this.inputTables.get(i - 1);
		for (String alias : n2a.getAllAliasesForBaseTable(t.getlocalName())) {
			if (partialResult.contains(alias)) {
				continue;
			}
			// partialResult.add(alias);
			if (i == this.inputTables.size()) {
				List<String> newResult = new ArrayList<String>(partialResult);
				newResult.add(alias);
				result.add(newResult);
				if (getOnlyFirst) {
					return;
				}
			} else {
				List<String> newResult = new ArrayList<String>(partialResult);
				// partialResult.add(alias);
				newResult.add(alias);
				traverseTables(i, n2a, newResult, result, getOnlyFirst);
				if (getOnlyFirst) {
					return;
				}
			}
		}

	}

	public void traverseTables(int i, NamesToAliases n2a, List<String> partialResult, List<List<String>> result,
			boolean getOnlyFirst, Map<String, Integer> counts) {
		i++;
		Table t = this.inputTables.get(i - 1);
		int previousCounts = 0;
		if (counts.containsKey(t.getName())) {
			previousCounts = 1 + counts.get(t.getName());
		}
		for (int a = previousCounts; a < n2a.getAllAliasesForBaseTable(t.getlocalName()).size(); a++) {
			String alias = n2a.getAllAliasesForBaseTable(t.getlocalName()).get(a);
			if (partialResult.contains(alias)) {
				continue;
			}
			// partialResult.add(alias);
			if (i == this.inputTables.size()) {
				List<String> newResult = new ArrayList<String>(partialResult);
				newResult.add(alias);
				result.add(newResult);
				if (getOnlyFirst) {
					return;
				}
			} else {
				List<String> newResult = new ArrayList<String>(partialResult);
				// partialResult.add(alias);
				newResult.add(alias);
				traverseTables(i, n2a, newResult, result, getOnlyFirst, counts);
				if (getOnlyFirst) {
					return;
				}
			}
		}

	}

	public void renameTables(List<String> aliases) {
		for (int i = 0; i < this.inputTables.size(); i++) {
			Table t = this.inputTables.get(i);
			String localAlias = t.getAlias();
			String globalAlias = aliases.get(i);
			t.setAlias(globalAlias);
			for (UnaryWhereCondition uwc : this.unaryWhereConditions) {
				for (Column c : uwc.getAllColumnRefs()) {
					// Column c = uwc.getAllColumnRefs().get(0);
					if (c.getAlias().equals(localAlias)) {
						c.setAlias(globalAlias);
					}
				}
			}
			for (NonUnaryWhereCondition nuwc : this.binaryWhereConditions) {
				for (Column c : nuwc.getAllColumnRefs()) {
					if (c.getAlias().equals(localAlias)) {
						c.setAlias(globalAlias);
					}
				}
			}
			for (Output o : this.outputs) {
				for (Column c : o.getObject().getAllColumnRefs()) {
					if (c.getAlias().equals(localAlias)) {
						c.setAlias(globalAlias);
					}
				}
			}
			for (Column c : this.groupBy) {
				if (c.getAlias().equals(localAlias)) {
					c.setAlias(globalAlias);
				}
			}
			for (Column c : this.orderBy) {
				if (c.getAlias().equals(localAlias)) {
					c.setAlias(globalAlias);
				}
			}
		}
	}

	public void renameTable(Table t, String globalAlias) {

		String localAlias = t.getAlias();
		t.setAlias(globalAlias);
		for (UnaryWhereCondition uwc : this.unaryWhereConditions) {
			for (Column c : uwc.getAllColumnRefs()) {
				// Column c = uwc.getAllColumnRefs().get(0);
				if (c.getAlias().equals(localAlias)) {
					c.setAlias(globalAlias);
				}
			}
		}
		for (NonUnaryWhereCondition nuwc : this.binaryWhereConditions) {
			for (Column c : nuwc.getAllColumnRefs()) {
				if (c.getAlias().equals(localAlias)) {
					c.setAlias(globalAlias);
				}
			}
		}
		for (Output o : this.outputs) {
			for (Column c : o.getObject().getAllColumnRefs()) {
				if (c.getAlias().equals(localAlias)) {
					c.setAlias(globalAlias);
				}
			}
		}
		for (Column c : this.groupBy) {
			if (c.getAlias().equals(localAlias)) {
				c.setAlias(globalAlias);
			}
		}
		for (Column c : this.orderBy) {
			if (c.getAlias().equals(localAlias)) {
				c.setAlias(globalAlias);
			}
		}
	}

	public void renameAliases(NamesToAliases n2a) {
		// Map<String, String> aliasToAlias = new HashMap<String, String>();
		Map<String, Integer> counts = new HashMap<String, Integer>();
		for (Table t : this.inputTables) {
			String localAlias = t.getAlias();
			String globalAlias;
			if (counts.containsKey(t.getlocalName())) {
				counts.put(t.getlocalName(), counts.get(t.getlocalName()) + 1);
				globalAlias = n2a.getGlobalAliasForBaseTable(t.getlocalName(), counts.get(t.getlocalName()));
			} else {
				counts.put(t.getlocalName(), 0);
				globalAlias = n2a.getGlobalAliasForBaseTable(t.getlocalName(), 0);
			}
			t.setAlias(globalAlias);
			for (UnaryWhereCondition uwc : this.unaryWhereConditions) {
				Column c = uwc.getAllColumnRefs().get(0);
				if (c.getAlias().equals(localAlias)) {
					c.setAlias(globalAlias);
				}
			}
			for (NonUnaryWhereCondition nuwc : this.binaryWhereConditions) {
				for (Column c : nuwc.getAllColumnRefs()) {
					if (c.getAlias().equals(localAlias)) {
						c.setAlias(globalAlias);
					}
				}
			}
			for (Output o : this.outputs) {
				for (Column c : o.getObject().getAllColumnRefs()) {
					if (c.getAlias().equals(localAlias)) {
						c.setAlias(globalAlias);
					}
				}
			}
			for (Column c : this.groupBy) {
				if (c.getAlias().equals(localAlias)) {
					c.setAlias(globalAlias);
				}
			}
			for (Column c : this.orderBy) {
				if (c.getAlias().equals(localAlias)) {
					c.setAlias(globalAlias);
				}
			}
		}

	}

	public void pushLimit(int limit) {
		// push limit in nested queries
		// This is correct only for CQ!!!
		if (limit > -1) {
			if (this.getLimit() > -1) {
				if (this.getLimit() < limit) {
					this.setLimit(limit);
					for (SQLQuery nested : this.getNestedSubqueries()) {
						nested.pushLimit(limit);
					}
				}
			} else {
				this.setLimit(limit);
				for (SQLQuery nested : this.getNestedSubqueries()) {
					nested.pushLimit(limit);
				}
			}
		}
	}

	public Set<Table> getAllReferencedTables() {
		Set<Table> result = new HashSet<Table>();
		result.addAll(this.getInputTables());

		for (SQLQuery u : this.unionqueries) {
			result.addAll(u.getAllReferencedTables());
		}
		for (SQLQuery n : this.nestedSelectSubqueries.keySet()) {
			result.addAll(n.getAllReferencedTables());
		}
		return result;
	}

	public Set<Table> getAllAttachedTables() {
		Set<Table> result = new HashSet<Table>();
		result.addAll(this.getInputTables());

		for (SQLQuery u : this.unionqueries) {
			result.add(new Table(u.getTemporaryTableName(), u.getTemporaryTableName()));
		}
		for (SQLQuery n : this.nestedSelectSubqueries.keySet()) {
			result.addAll(n.getAllAttachedTables());
		}
		return result;
	}

	public Set<Column> getAllReferencedColumns() {
		Set<Column> result = new HashSet<Column>();

		for (Table t : this.inputTables) {
			for (Column c : this.getAllColumns()) {
				if (t.getAlias().equals(c.getAlias())) {
					result.add(new Column(t.getName(), c.getName()));
				}
			}
		}

		for (SQLQuery u : this.unionqueries) {
			result.addAll(u.getAllReferencedColumns());
		}
		for (SQLQuery n : this.nestedSelectSubqueries.keySet()) {
			result.addAll(n.getAllReferencedColumns());
		}
		return result;
	}

	public void refactorForFederation() {
		if (!this.getInputTables().isEmpty()) {
			String dbID = this.getInputTables().get(0).getDBName();
			log.debug("dbid:" + dbID);
			if (dbID == null) {
				// not federated
				return;
			}

			for (int i = 0; i < this.getInputTables().size(); i++) {
				Table t = this.getInputTables().get(i);
				Table replace = new Table();
				String db = t.getDBName();
				// System.out.println(db);
				String schema = DBInfoReaderDB.dbInfo.getDB(db).getSchema();
				replace.setName(t.getName().substring(db.length() + 1));
				replace.setAlias(t.getAlias());

				if (!replace.getName().startsWith(schema + ".")) {
					replace.setName(schema + "." + replace.getName());
				}

				this.inputTables.remove(i);
				this.inputTables.add(i, replace);
			}

		}

	}

	public void generateRefCols(Map<String, Set<String>> refCols) {
		List<Column> cols = this.getAllColumns();
		Set<String> colsForT;
		for (Table t : this.getInputTables()) {
			if (refCols.containsKey(t.getName())) {
				colsForT = refCols.get(t.getName());
			} else {
				colsForT = new HashSet<String>();
				refCols.put(t.getName(), colsForT);
			}
			for (Column c : cols) {
				if (c.getAlias() == null) {
					c.setAlias(t.getAlias());
				}
				if (c.getAlias().equals(t.getAlias())) {
					colsForT.add(c.getName());
				}
			}
		}
		for (SQLQuery u : this.unionqueries) {
			u.generateRefCols(refCols);
		}
		for (SQLQuery n : this.nestedSelectSubqueries.keySet()) {
			n.generateRefCols(refCols);
		}

	}

	public boolean isMaterialised() {
		return materialised;
	}

	public void setMaterialised(boolean m) {
		materialised = m;
	}

	public void addOutput(String alias, String outputName) {
		Column c = new Column(alias, outputName);
		this.outputs.add(new Output(outputName, c));

	}

	public void addOutput(String aliasTableName, String columnName, String alias) {
		this.outputs.add(new Output(alias, new Column(aliasTableName, columnName)));
	}

	public void removeInfo() {
		unaryWhereConditions = new ArrayList<UnaryWhereCondition>();
		outputs = new ArrayList<Output>();
		inputTables = new ArrayList<Table>();
		binaryWhereConditions = new ArrayList<NonUnaryWhereCondition>();
		nestedSelectSubqueries = new HashMap<SQLQuery, String>();
		limit = -1;
		groupBy = new ArrayList<Column>();
		orderBy = new ArrayList<ColumnOrderBy>();

	}

	public void putNestedNode(Node node) {
		this.nestedNode = node;

	}

	public Node getNestedNode() {
		return this.nestedNode;

	}

	public void renameTable(String oldName, String newName) {
		boolean exists = false;
		for (Table t : this.inputTables) {
			if (t.getAlias().equals(oldName)) {
				t.setAlias(newName);
				t.setName(newName);
				exists = true;
				break;
			}
		}
		for (SQLQuery nested : this.getNestedSelectSubqueries().keySet()) {
			if (this.getNestedSubqueryAlias(nested).equals(oldName)) {
				nestedSelectSubqueries.put(nested, newName);
				exists = true;
				break;
			}
		}
		for (int i = 0; i < this.unionqueries.size(); i++) {
			SQLQuery u = this.unionqueries.get(i);
			if (u.getTemporaryTableName().equals(oldName)) {
				this.unionqueries.remove(i);
				SQLQuery dummyUnion = new SQLQuery();
				dummyUnion.setTemporaryTableName(newName);
				dummyUnion.setOutputColumnsDistinct(u.getOutputColumnsDistinct());
				this.unionqueries.add(i, dummyUnion);
				exists = true;
				break;
			}
		}
		if (exists) {
			for (Column c : this.getAllColumns()) {
				if (c.getName().equals(oldName)) {
					c.setName(newName);
				}
			}
		}
	}

	public HashCode getHashId() {
		return hashId;
	}

	public void setHashId(HashCode hashId) {
		this.hashId = hashId;
	}

	public void removeOutputs() {
		this.outputs = new ArrayList<Output>();

	}

	public void addInputTable(Table lastTable) {
		this.inputTables.add(lastTable);

	}

	public void setExistsInCache(boolean b) {
		this.existsInCache = b;
	}

	public boolean existsInCache() {
		return this.existsInCache;
	}

	public Map<String, String> renameOracleOutputs() {
		Map<String, String> result = new HashMap<String, String>();
		for (Output o : this.outputs) {
			if (o.getOutputName().length() > 29) {
				String shortOutput = o.getOutputName().substring(0, 29);
				while (result.containsKey(shortOutput)) {
					shortOutput = shortOutput.substring(0, shortOutput.length() - 1);
				}
				result.put(shortOutput.toUpperCase(), o.getOutputName());
				o.setOutputName(shortOutput);
			}
		}
		log.debug("sending oracle corresponding columns:" + result);
		return result;
	}

	public void setJoinNode(Node join) {
		this.joinNode = join;

	}

	public Node getJoinNode() {
		return this.joinNode;
	}

	public void addJoinOperand(Operand joinOp) {
		this.joinOperands.add(joinOp);

	}

	public void setIsCreateIndex() {
		this.isCreateIndex = true;

	}

	public void addInputTableIfNotExists(Table table, int index) {
		if (!this.inputTables.contains(table) && !table.getName().equals(this.getTemporaryTableName())) {
			this.inputTables.add(index, table);
		}

	}

	public List<Operand> getJoinOperands() {
		return joinOperands;
	}

	public void addColumnAliases() {
		// when we have only 1 input table, make sure all columns have table
		// aliases
		String alias = this.getInputTables().get(0).getAlias();
		for (Column c : this.getAllColumns()) {
			if (c.getAlias() == null) {
				c.setAlias(alias);
			}
		}

	}

	public boolean containsIputTable(String alias) {
		for (Table t : this.inputTables) {
			if (t.getAlias().equals(alias)) {
				return true;
			}
		}
		return false;
	}

	public List<List<String>> getListOfAliases(NamesToAliases n2a, boolean getOnlyFirst, Map<String, Integer> counts) {
		List<List<String>> result = new ArrayList<List<String>>();
		Map<String, Integer> countsCloned = new HashMap<String, Integer>(counts);
		if (this.inputTables.isEmpty()) {
			return result;
		}
		List<String> partialResult = new ArrayList<String>();
		for (Table t : this.inputTables) {
			// String localAlias = t.getAlias();
			// String globalAlias;
			if (counts.containsKey(t.getlocalName())) {
				counts.put(t.getlocalName(), counts.get(t.getlocalName()) + 1);
				n2a.getGlobalAliasForBaseTable(t.getlocalName(), counts.get(t.getlocalName()));
			} else {
				counts.put(t.getlocalName(), 0);
				n2a.getGlobalAliasForBaseTable(t.getlocalName(), 0);
			}
		}

		// for (int i=0;i<this.inputTables.size();i++) {
		// Table t=this.inputTables.get(i);

		// for(String alias:n2a.getAllAliasesForBaseTable(t.getlocalName())){
		// partialResult.add(alias);
		traverseTables(0, n2a, new ArrayList<String>(partialResult), result, getOnlyFirst, countsCloned);
		// System.out.println(result);
		// }
		// i++;
		// }
		return result;
	}

	public boolean isDrop() {
		return isDrop;
	}

	public void setDrop(boolean isDrop) {
		this.isDrop = isDrop;
	}

	public boolean isSelectAllFromInternal() {
		return ((this.isSelectAll() || this.getOutputs().isEmpty()) && this.inputTables.size() == 1
				&& this.binaryWhereConditions.isEmpty() && this.unaryWhereConditions.isEmpty()
				&& this.nestedSelectSubqueries.isEmpty() && this.unionqueries.isEmpty()
				&& !this.getInputTables().get(0).isFederated() && this.orderBy.isEmpty() && this.groupBy.isEmpty()
				&& this.nestedNode == null);
	}

	public Set<Column> getAllJoinColumns() {
		Set<Column> result = new HashSet<Column>();
		for (NonUnaryWhereCondition nuwc : this.getBinaryWhereConditions()) {
			if (nuwc.getOperator().equals("=")) {
				if (!(nuwc.getLeftOp().getAllColumnRefs().isEmpty())) {
					result.add(nuwc.getLeftOp().getAllColumnRefs().get(0));
				}
				if (!(nuwc.getRightOp().getAllColumnRefs().isEmpty())) {
					result.add(nuwc.getRightOp().getAllColumnRefs().get(0));
				}

			} else {
				if (!(nuwc.getLeftOp().getAllColumnRefs().isEmpty())
						&& !(nuwc.getRightOp().getAllColumnRefs().isEmpty())) {
					// range join
					return result;
				}
				if (!(nuwc.getLeftOp().getAllColumnRefs().isEmpty())) {
					result.add(nuwc.getLeftOp().getAllColumnRefs().get(0));
				}
				if (!(nuwc.getRightOp().getAllColumnRefs().isEmpty())) {
					result.add(nuwc.getRightOp().getAllColumnRefs().get(0));
				}
			}
		}
		return result;
	}

	public String getOutputSQL() {
		StringBuilder output = new StringBuilder();
		output.append("select ");
		// }
		String separator = "";
		if (this.isSelectAll() || this.getOutputs().isEmpty()) {
			output.append("*");
		} else {
			if (this.isOutputColumnsDinstict()) {
				output.append("distinct ");
			}
			for (Output c : getOutputs()) {
				output.append(separator);
				separator = ", ";
				output.append(c.toString());
			}
			/*
			 * for (Function f : outputFunctions) { output.append(separator);
			 * separator = ", "; output.append(f.toString()); }
			 */
		}
		separator = "";
		// if (!this.isHasUnionRootNode()) {
		output.append(" from ");
		String result = output.toString().replaceAll("\"", "\\\\\"");
		return result;
	}

	public String getStringOutputs() {
		return stringOutputs;
	}

	public void setStringOutputs(String stringOutputs) {
		this.stringOutputs = stringOutputs;
	}

	public boolean sipJoinIsLast() {
		if (this.inputTables.isEmpty()) {
			return false;
		}
		Table t = this.inputTables.get(inputTables.size() - 1);
		if (t.getName().equalsIgnoreCase("siptable")) {

			return true;
		}
		return false;
	}

	public boolean containsSip() {
		for (Table t : this.inputTables) {
			if (t.getName().equalsIgnoreCase("siptable")) {

				return true;
			}
		}
		return false;
	}

	public int getLeftOfSip() {
		for (int i = 0; i < inputTables.size(); i++) {
			if (inputTables.get(i).getName().equals("siptable")) {
				return i;
			}
		}
		return 0;
	}

	public void setSQL(String string) {
		this.sql = string;

	}

	public void setStringSQL() {
		this.isStringSQL = true;

	}

	public String getSqlForPartition(int i) {
		
		String splitCondition="";

		if (this.tableToSplit > this.inputTables.size() && i > 0) {
			return null;
		}

		StringBuilder output = new StringBuilder();
		String separator = "";

		// if (!this.isHasUnionRootNode()) {
		output.append("select ");
		// }
		separator = "";
		if (this.isSelectAll() || this.getOutputs().isEmpty()) {
			output.append("*");
		} else {
			if (this.isOutputColumnsDinstict()) {
				output.append("distinct ");
			}
			for (Output c : getOutputs()) {
				output.append(separator);
				separator = ", \n";
				output.append(c.toString());
			}
			/*
			 * for (Function f : outputFunctions) { output.append(separator);
			 * separator = ", "; output.append(f.toString()); }
			 */
		}
		separator = "";
		// if (!this.isHasUnionRootNode()) {
		output.append(" from \n");
		// }
		if (this.getJoinType() != null) {

			for (int tableNo = 0; tableNo < this.inputTables.size() - 1; tableNo++) {
				output.append("(");

				output.append(inputTables.get(tableNo).toString().toLowerCase());

				output.append(" ");
				output.append(getJoinType());
				output.append(" ");
			}

			output.append(inputTables.get(this.inputTables.size() - 1).toString().toLowerCase());

			for (int joinOp = joinOperands.size() - 1; joinOp > -1; joinOp--) {
				output.append(" on ");
				output.append(joinOperands.get(joinOp).toString());
				output.append(")");

			}

		} else if (!this.unionqueries.isEmpty()) {
			// UNIONS
			return "";

		} else {
			if (!this.nestedSelectSubqueries.isEmpty()) {
				// nested select subqueries
				for (SQLQuery nested : getNestedSelectSubqueries().keySet()) {
					String alias = this.nestedSelectSubqueries.get(nested);
					output.append(separator);
					output.append("(select ");
					if (nested.isOutputColumnsDinstict()) {
						output.append("distinct ");
					}
					output.append("* from \n");
					output.append(nested.getResultTableName());
					output.append(")");
					// if (nestedSelectSubqueryAlias != null) {
					output.append(" ");
					output.append(alias);
					separator = ", \n";
				} // }
			} // else {

			// output.append("(");
			/*
			 * output.append(separator); Table first = this.inputTables.get(0);
			 * output.append(first.getName() + "_" + i); output.append(" ");
			 * output.append(first.getAlias()); separator = " CROSS JOIN ";
			 */
			for (int t = 0; t < inputTables.size(); t++) {

				output.append(separator);
//				if (t < this.tableToSplit) {
//					output.append(this.inputTables.get(t).toString());
//				} else 
					if (t == this.tableToSplit) {
					Table tbl = this.inputTables.get(t);
					/*output.append(tbl.getName());
					output.append("_");
					output.append(i);
					output.append(" ");
					output.append(tbl.getAlias());*/
					output.append("memorywrapper"+tbl.getName());
					output.append(" ");
					output.append(tbl.getAlias());
					splitCondition=" "+tbl.getAlias()+".s>"+i+" ";
					
				} else {
					Table tbl = this.inputTables.get(t);
					if(tbl.getName().equals("dictionary")){
						output.append(tbl.getName());
					}
					else{
						output.append("memorywrapper" + tbl.getName());
					}
					
					output.append(" ");
					output.append(tbl.getAlias());
				}
				separator = " CROSS JOIN ";

			}
			// output.append(")");

		}
		separator = "";
		output.append(" WHERE ");
		for (NonUnaryWhereCondition wc : getBinaryWhereConditions()) {
			output.append(separator);
			output.append(wc.toString());
			separator = " and \n";
		}
		for (UnaryWhereCondition wc : getUnaryWhereConditions()) {
			output.append(separator);
			output.append(wc.toString());
			separator = " and \n";
		}
		if(!splitCondition.equals("")){
			output.append(separator);
			output.append(splitCondition);
		}

		if (this.getJoinType() != null) {
			output.append(") ");
		}

		if (!groupBy.isEmpty()) {
			separator = "";
			output.append(" \ngroup by ");
			for (Column c : getGroupBy()) {
				output.append(separator);
				output.append(c.toString());
				separator = ", ";
			}
		}
		if (!orderBy.isEmpty()) {
			separator = "";
			output.append(" \norder by ");
			for (ColumnOrderBy c : getOrderBy()) {
				output.append(separator);
				output.append(c.toString());
				separator = ", ";
			}
		}

		// output.append(";");
		return output.toString();
	}

	public void computeTableToSplit(int partitions) {
		for (int i = 0; i < inputTables.size(); i++) {
			Table t = inputTables.get(i);
			boolean existsFilter = false;
			boolean inv = t.getName().startsWith("inv");
			if (t.getName().equals("dictionary")) {
				this.tableToSplit = inputTables.size() + 1;
				return;
			}
			NonUnaryWhereCondition toAdd=null;
			for (NonUnaryWhereCondition nuwc : this.binaryWhereConditions) {
				if (nuwc.getLeftOp() instanceof Constant && nuwc.getRightOp() instanceof Column) {
					Column c = (Column) nuwc.getRightOp();
					if (t.getAlias().equals(c.getAlias())) {
						if (c.getName().equals("s")) {
							toAdd=new NonUnaryWhereCondition(new Column(t.getAlias(), "s"), new Constant(Long.parseLong(nuwc.getLeftOp().toString()) % partitions), ">");
							//t.setName(t.getName() + "_" + Long.parseLong(nuwc.getLeftOp().toString()) % partitions);
							existsFilter = true;
							break;
						}
					}
				} else if (nuwc.getRightOp() instanceof Constant && nuwc.getLeftOp() instanceof Column) {
					Column c = (Column) nuwc.getLeftOp();
					if (t.getAlias().equals(c.getAlias())) {
						if (c.getName().equals("s")) {
							toAdd=new NonUnaryWhereCondition(new Column(t.getAlias(), "s"), new Constant(Long.parseLong(nuwc.getRightOp().toString()) % partitions), ">");
							//t.setName(t.getName() + "_" + Long.parseLong(nuwc.getRightOp().toString()) % partitions);
							existsFilter = true;
							break;
						
					}
				}
			}
			}
			if(toAdd!=null){
				this.binaryWhereConditions.add(toAdd);
			}
			if (!existsFilter) {
				this.tableToSplit = i;
				return;
			}
		}
	}

	public void invertColumns() {
		Set<String> inverses=new HashSet<String>();
		for(Table t:this.inputTables){
			if(t.getName().startsWith("inv")){
				inverses.add(t.getAlias());
			}
		}
		if(inverses.isEmpty()){
			return;
		}
		for(Column c:this.getAllColumns()){
			if(inverses.contains(c.getAlias())){
				if(c.getName().equals("s")){
					c.setName("o");
				}
				else{
					c.setName("s");
				}
			}
		}
		
	}

}
