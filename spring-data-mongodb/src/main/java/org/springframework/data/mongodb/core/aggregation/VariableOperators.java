/*
 * Copyright 2016-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.core.aggregation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.VariableOperators.Let.ExpressionVariable;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Gateway to {@literal variable} aggregation operations.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @since 1.10
 */
public class VariableOperators {

	/**
	 * Starts building new {@link Map} that applies an {@link AggregationExpression} to each item of a referenced array
	 * and returns an array with the applied results.
	 *
	 * @param fieldReference must not be {@literal null}.
	 * @return
	 */
	public static Map.AsBuilder mapItemsOf(String fieldReference) {
		return Map.itemsOf(fieldReference);
	}

	/**
	 * Starts building new {@link Map} that applies an {@link AggregationExpression} to each item of a referenced array
	 * and returns an array with the applied results.
	 *
	 * @param expression must not be {@literal null}.
	 * @return
	 */
	public static Map.AsBuilder mapItemsOf(AggregationExpression expression) {
		return Map.itemsOf(expression);
	}

	/**
	 * Start creating new {@link Let} that allows definition of {@link ExpressionVariable} that can be used within a
	 * nested {@link AggregationExpression}.
	 *
	 * @param variables must not be {@literal null}.
	 * @return
	 */
	public static Let.LetBuilder define(ExpressionVariable... variables) {
		return Let.define(variables);
	}

	/**
	 * Start creating new {@link Let} that allows definition of {@link ExpressionVariable} that can be used within a
	 * nested {@link AggregationExpression}.
	 *
	 * @param variables must not be {@literal null}.
	 * @return
	 */
	public static Let.LetBuilder define(Collection<ExpressionVariable> variables) {
		return Let.define(variables);
	}

	/**
	 * {@link AggregationExpression} for {@code $map}.
	 */
	public static class Map implements AggregationExpression {

		private Object sourceArray;
		private String itemVariableName;
		private AggregationExpression functionToApply;

		private Map(Object sourceArray, String itemVariableName, AggregationExpression functionToApply) {

			Assert.notNull(sourceArray, "SourceArray must not be null!");
			Assert.notNull(itemVariableName, "ItemVariableName must not be null!");
			Assert.notNull(functionToApply, "FunctionToApply must not be null!");

			this.sourceArray = sourceArray;
			this.itemVariableName = itemVariableName;
			this.functionToApply = functionToApply;
		}

		/**
		 * Starts building new {@link Map} that applies an {@link AggregationExpression} to each item of a referenced array
		 * and returns an array with the applied results.
		 *
		 * @param fieldReference must not be {@literal null}.
		 * @return
		 */
		public static AsBuilder itemsOf(final String fieldReference) {

			Assert.notNull(fieldReference, "FieldReference must not be null!");

			return new AsBuilder() {

				@Override
				public FunctionBuilder as(final String variableName) {

					Assert.notNull(variableName, "VariableName must not be null!");

					return new FunctionBuilder() {

						@Override
						public Map andApply(final AggregationExpression expression) {

							Assert.notNull(expression, "AggregationExpression must not be null!");
							return new Map(Fields.field(fieldReference), variableName, expression);
						}
					};
				}

			};
		}

		/**
		 * Starts building new {@link Map} that applies an {@link AggregationExpression} to each item of a referenced array
		 * and returns an array with the applied results.
		 *
		 * @param source must not be {@literal null}.
		 * @return
		 */
		public static AsBuilder itemsOf(final AggregationExpression source) {

			Assert.notNull(source, "AggregationExpression must not be null!");

			return new AsBuilder() {

				@Override
				public FunctionBuilder as(final String variableName) {

					Assert.notNull(variableName, "VariableName must not be null!");

					return new FunctionBuilder() {

						@Override
						public Map andApply(final AggregationExpression expression) {

							Assert.notNull(expression, "AggregationExpression must not be null!");
							return new Map(source, variableName, expression);
						}
					};
				}
			};
		}

		/* (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.aggregation.AggregationExpression#toDocument(org.springframework.data.mongodb.core.aggregation.AggregationOperationContext)
		 */
		@Override
		public Document toDocument(final AggregationOperationContext context) {
			return toMap(ExposedFields.synthetic(Fields.fields(itemVariableName)), context);
		}

		private Document toMap(ExposedFields exposedFields, AggregationOperationContext context) {

			Document map = new Document();
			InheritingExposedFieldsAggregationOperationContext operationContext = new InheritingExposedFieldsAggregationOperationContext(
					exposedFields, context);

			Document input;
			if (sourceArray instanceof Field) {
				input = new Document("input", context.getReference((Field) sourceArray).toString());
			} else {
				input = new Document("input", ((AggregationExpression) sourceArray).toDocument(context));
			}

			map.putAll(context.getMappedObject(input));
			map.put("as", itemVariableName);
			map.put("in",
					functionToApply.toDocument(new NestedDelegatingExpressionAggregationOperationContext(operationContext,
							Collections.singleton(Fields.field(itemVariableName)))));

			return new Document("$map", map);
		}

		public interface AsBuilder {

			/**
			 * Define the {@literal variableName} for addressing items within the array.
			 *
			 * @param variableName must not be {@literal null}.
			 * @return
			 */
			FunctionBuilder as(String variableName);
		}

		public interface FunctionBuilder {

			/**
			 * Creates new {@link Map} that applies the given {@link AggregationExpression} to each item of the referenced
			 * array and returns an array with the applied results.
			 *
			 * @param expression must not be {@literal null}.
			 * @return
			 */
			Map andApply(AggregationExpression expression);
		}
	}

	/**
	 * {@link AggregationExpression} for {@code $let} that binds {@link AggregationExpression} to variables for use in the
	 * specified {@code in} expression, and returns the result of the expression.
	 *
	 * @author Christoph Strobl
	 * @since 1.10
	 */
	public static class Let implements AggregationExpression {

		private final List<ExpressionVariable> vars;
		private final AggregationExpression expression;

		private Let(List<ExpressionVariable> vars, AggregationExpression expression) {

			this.vars = vars;
			this.expression = expression;
		}

		/**
		 * Start creating new {@link Let} by defining the variables for {@code $vars}.
		 *
		 * @param variables must not be {@literal null}.
		 * @return
		 */
		public static LetBuilder define(final Collection<ExpressionVariable> variables) {

			Assert.notNull(variables, "Variables must not be null!");

			return new LetBuilder() {

				@Override
				public Let andApply(final AggregationExpression expression) {

					Assert.notNull(expression, "Expression must not be null!");
					return new Let(new ArrayList<ExpressionVariable>(variables), expression);
				}
			};
		}

		/**
		 * Start creating new {@link Let} by defining the variables for {@code $vars}.
		 *
		 * @param variables must not be {@literal null}.
		 * @return
		 */
		public static LetBuilder define(final ExpressionVariable... variables) {

			Assert.notNull(variables, "Variables must not be null!");

			return new LetBuilder() {

				@Override
				public Let andApply(final AggregationExpression expression) {

					Assert.notNull(expression, "Expression must not be null!");
					return new Let(Arrays.asList(variables), expression);
				}
			};
		}

		public interface LetBuilder {

			/**
			 * Define the {@link AggregationExpression} to evaluate.
			 *
			 * @param expression must not be {@literal null}.
			 * @return
			 */
			Let andApply(AggregationExpression expression);
		}

		/* (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.aggregation.AggregationExpression#toDocument(org.springframework.data.mongodb.core.aggregation.AggregationOperationContext)
		 */
		@Override
		public Document toDocument(final AggregationOperationContext context) {
			return toLet(ExposedFields.synthetic(Fields.fields(getVariableNames())), context);
		}

		String[] getVariableNames() {

			String[] varNames = new String[this.vars.size()];
			for (int i = 0; i < this.vars.size(); i++) {
				varNames[i] = this.vars.get(i).variableName;
			}

			return varNames;
		}

		private Document toLet(ExposedFields exposedFields, AggregationOperationContext context) {

			Document letExpression = new Document();
			Document mappedVars = new Document();
			InheritingExposedFieldsAggregationOperationContext operationContext = new InheritingExposedFieldsAggregationOperationContext(
					exposedFields, context);

			for (ExpressionVariable var : this.vars) {
				mappedVars.putAll(getMappedVariable(var, context));
			}

			letExpression.put("vars", mappedVars);
			letExpression.put("in", getMappedIn(operationContext));

			return new Document("$let", letExpression);
		}

		private Document getMappedVariable(ExpressionVariable var, AggregationOperationContext context) {

			return new Document(var.variableName,
					var.expression instanceof AggregationExpression ? ((AggregationExpression) var.expression).toDocument(context)
							: var.expression);
		}

		private Object getMappedIn(AggregationOperationContext context) {
			return expression.toDocument(new NestedDelegatingExpressionAggregationOperationContext(context,
					this.vars.stream().map(var -> Fields.field(var.variableName)).collect(Collectors.toList())));
		}

		/**
		 * @author Christoph Strobl
		 */
		public static class ExpressionVariable {

			private final @Nullable String variableName;
			private final @Nullable Object expression;

			/**
			 * Creates new {@link ExpressionVariable}.
			 *
			 * @param variableName can be {@literal null}.
			 * @param expression can be {@literal null}.
			 */
			private ExpressionVariable(@Nullable String variableName, @Nullable Object expression) {

				this.variableName = variableName;
				this.expression = expression;
			}

			/**
			 * Create a new {@link ExpressionVariable} with given name.
			 *
			 * @param variableName must not be {@literal null}.
			 * @return never {@literal null}.
			 */
			public static ExpressionVariable newVariable(String variableName) {

				Assert.notNull(variableName, "VariableName must not be null!");
				return new ExpressionVariable(variableName, null);
			}

			/**
			 * Create a new {@link ExpressionVariable} with current name and given {@literal expression}.
			 *
			 * @param expression must not be {@literal null}.
			 * @return never {@literal null}.
			 */
			public ExpressionVariable forExpression(AggregationExpression expression) {

				Assert.notNull(expression, "Expression must not be null!");
				return new ExpressionVariable(variableName, expression);
			}

			/**
			 * Create a new {@link ExpressionVariable} with current name and given {@literal expressionObject}.
			 *
			 * @param expressionObject must not be {@literal null}.
			 * @return never {@literal null}.
			 */
			public ExpressionVariable forExpression(Document expressionObject) {

				Assert.notNull(expressionObject, "Expression must not be null!");
				return new ExpressionVariable(variableName, expressionObject);
			}
		}
	}
}
