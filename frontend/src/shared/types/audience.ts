export type AudienceField =
    'age'
    | 'gender'
    | 'registeredAt'
    | 'membershipTier'
    | 'orderCount'
    | 'totalSpending'
    | 'lastOrderAt'
    | 'lastLoginAt';
export type AudienceOperator = '=' | '!=' | '>' | '>=' | '<' | '<=' | 'IN' | 'NOT IN';
export type AudienceCondition = {
    field: AudienceField;
    operator: AudienceOperator;
    value: string | number | string[] | number[]
};
export type AudienceRules = { version: 1; match: 'ALL'; conditions: AudienceCondition[] };
export type AudienceSegment = {
    id: string;
    name: string;
    description: string | null;
    rules: AudienceRules;
    createdByUserId: string;
    createdAt: string;
    updatedAt: string
};
export type Page<T> = { data: T[]; page: { number: number; size: number; totalElements: number; totalPages: number } };
export type AudiencePreview = { count: number; sample: { memberId: string; displayName: string }[] };
