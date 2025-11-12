create type meta.attribute_type as enum ('SINGULAR', 'PLURAL');

alter type meta.attribute_type owner to postgres;

create type meta.attribute_category as enum ('BASIC', 'ENTITY');

alter type meta.attribute_category owner to postgres;

create type meta.basic_type as enum ('STRING', 'BOOLEAN', 'INTEGER', 'LONG', 'OFFSET_DATE_TIME', 'TIMESTAMP', 'ENUM', 'DOUBLE', 'POINT');

alter type meta.basic_type owner to postgres;

create type predicate.node_type as enum ('LOGICAL_OPERATOR', 'COMPARISON_OPERATOR', 'VALUE_CONSTANT', 'PATH_EXPRESSION');

alter type predicate.node_type owner to postgres;

create type predicate.operator_type as enum ('AND', 'OR', 'NOT', 'EQ', 'NE', 'GT', 'LT', 'GOE', 'LOE', 'LIKE', 'STARTS_WITH', 'ENDS_WITH', 'CONTAINS', 'IN', 'NOT_IN', 'BETWEEN', 'IS_NULL', 'IS_NOT_NULL', 'DISTANCE_SPHERE');

alter type predicate.operator_type owner to postgres;

create table ri.site
(
    is_research boolean,
    id          varchar(255) not null
        primary key,
    site_name   varchar(255),
    geometry    geometry
);

alter table ri.site
    owner to postgres;

create table ri.base_station
(
    id              varchar(255) not null
        primary key,
    name_in_nms     varchar(255),
    site_id         varchar(255)
        constraint fklqswy7od0wada8ep1ra6jqt20
            references ri.site,
    status          varchar(255)
        constraint base_station_status_check
            check ((status)::text = ANY
        ((ARRAY ['CREATED'::character varying, 'ACTIVE'::character varying, 'CANCELLED'::character varying])::text[])),
    bs_date_time    timestamp with time zone,
    rating          integer,
    coverage_radius double precision
);

comment on column ri.base_station.rating is 'Рейтинг базовой станции (целое число)';

alter table ri.base_station
    owner to postgres;

create index idx_base_station_rating
    on ri.base_station (rating);

create table meta.meta_entity
(
    id   uuid default gen_random_uuid() not null
        primary key,
    name varchar                        not null
);

comment on table meta.meta_entity is 'Мета-описание бизнес-сущностей';

alter table meta.meta_entity
    owner to postgres;

create table meta.meta_enum
(
    id          uuid default gen_random_uuid() not null
        primary key,
    name        varchar(255)                   not null
        unique,
    description text,
    class_name  varchar(500)
);

comment on table meta.meta_enum is 'Мета-описание перечислений (enum)';

alter table meta.meta_enum
    owner to postgres;

create index idx_meta_enum_name
    on meta.meta_enum (name);

create table meta.meta_attribute
(
    id                       uuid    default gen_random_uuid() not null
        primary key,
    name                     varchar                           not null,
    entity_id                uuid                              not null
        constraint fk_meta_attribute_entity
            references meta.meta_entity,
    type                     meta.attribute_type               not null,
    attribute_category       meta.attribute_category           not null,
    basic_type               meta.basic_type,
    attribute_entity_type_id uuid
        constraint fk_meta_attribute_entity_type
            references meta.meta_entity,
    is_bidirectional         boolean default false             not null,
    related_attribute_id     uuid
        constraint fk_meta_attribute_related
            references meta.meta_attribute,
    meta_enum_id             uuid
        constraint fk_attribute_enum
            references meta.meta_enum
            on delete set null,
    constraint chk_basic_type_required
        check ((attribute_category <> 'BASIC'::meta.attribute_category) OR (basic_type IS NOT NULL)),
    constraint chk_entity_type_required
        check ((attribute_category <> 'ENTITY'::meta.attribute_category) OR (attribute_entity_type_id IS NOT NULL)),
    constraint chk_enum_consistency
        check ((basic_type <> 'ENUM'::meta.basic_type) OR (meta_enum_id IS NOT NULL)),
    constraint chk_related_attribute_consistency
        check ((NOT is_bidirectional) OR (related_attribute_id IS NOT NULL)),
    constraint chk_plural_entity_only
        check ((type <> 'PLURAL'::meta.attribute_type) OR (attribute_category = 'ENTITY'::meta.attribute_category))
);

comment on table meta.meta_attribute is 'Мета-описание атрибутов бизнес-сущностей';

alter table meta.meta_attribute
    owner to postgres;

create index idx_meta_attribute_entity_id
    on meta.meta_attribute (entity_id);

create index idx_meta_attribute_entity_type_id
    on meta.meta_attribute (attribute_entity_type_id);

create index idx_meta_attribute_related_id
    on meta.meta_attribute (related_attribute_id);

create index idx_meta_attribute_name
    on meta.meta_attribute (name);

create table meta.meta_enum_value
(
    id            uuid default gen_random_uuid() not null
        primary key,
    name          varchar(255)                   not null,
    storage_value varchar(255)                   not null,
    description   text,
    order_index   integer,
    meta_enum_id  uuid                           not null
        constraint fk_enum_value_enum
            references meta.meta_enum
            on delete cascade,
    constraint uq_enum_value_name
        unique (meta_enum_id, name),
    constraint uq_enum_storage_value
        unique (meta_enum_id, storage_value)
);

comment on table meta.meta_enum_value is 'Значения перечислений';

alter table meta.meta_enum_value
    owner to postgres;

create index idx_enum_value_enum
    on meta.meta_enum_value (meta_enum_id);

create index idx_enum_value_order
    on meta.meta_enum_value (order_index);

create table predicate.predicate_path_expression
(
    id                  uuid default gen_random_uuid() not null
        primary key,
    root_attribute_id   uuid                           not null
        constraint fk_path_root_attribute
            references meta.meta_attribute
            on delete cascade,
    target_attribute_id uuid
        constraint fk_path_target_attribute
            references meta.meta_attribute
            on delete set null
);

comment on table predicate.predicate_path_expression is 'Выражение пути для навигации по связям между сущностями';

alter table predicate.predicate_path_expression
    owner to postgres;

create index idx_path_expression_root
    on predicate.predicate_path_expression (root_attribute_id);

create index idx_path_expression_target
    on predicate.predicate_path_expression (target_attribute_id);

create table predicate.predicate_path_expression_attributes
(
    path_expression_id uuid    not null
        constraint fk_path_expression_attr_expr
            references predicate.predicate_path_expression
            on delete cascade,
    meta_attribute_id  uuid    not null
        constraint fk_path_expression_attr_attr
            references meta.meta_attribute
            on delete cascade,
    attribute_order    integer not null,
    primary key (path_expression_id, meta_attribute_id)
);

comment on table predicate.predicate_path_expression_attributes is 'Связующая таблиция для атрибутов пути';

alter table predicate.predicate_path_expression_attributes
    owner to postgres;

create table predicate.predicate_node
(
    id                 uuid default gen_random_uuid() not null
        primary key,
    node_type          predicate.node_type            not null,
    operator_type      predicate.operator_type,
    meta_attribute_id  uuid
        constraint fk_node_meta_attribute
            references meta.meta_attribute
            on delete set null,
    path_expression_id uuid
        constraint fk_node_path_expression
            references predicate.predicate_path_expression
            on delete cascade,
    value_id           uuid,
    left_operand_id    uuid
        constraint fk_node_left_operand
            references predicate.predicate_node
            on delete cascade,
    right_operand_id   uuid
        constraint fk_node_right_operand
            references predicate.predicate_node
            on delete cascade,
    constraint chk_node_path_expression
        check ((node_type <> 'PATH_EXPRESSION'::predicate.node_type) OR (path_expression_id IS NOT NULL)),
    constraint chk_node_value_constant
        check ((node_type <> 'VALUE_CONSTANT'::predicate.node_type) OR (value_id IS NOT NULL)),
    constraint chk_logical_operator
        check ((node_type <> 'LOGICAL_OPERATOR'::predicate.node_type) OR
               ((operator_type IS NOT NULL) AND (left_operand_id IS NOT NULL))),
    constraint chk_comparison_operator
        check ((node_type <> 'COMPARISON_OPERATOR'::predicate.node_type) OR (operator_type IS NOT NULL)),
    constraint chk_not_operator
        check ((operator_type <> 'NOT'::predicate.operator_type) OR (right_operand_id IS NULL)),
    constraint chk_and_or_operator
        check ((operator_type <> ALL (ARRAY ['AND'::predicate.operator_type, 'OR'::predicate.operator_type])) OR
               ((left_operand_id IS NOT NULL) AND (right_operand_id IS NOT NULL))),
    constraint chk_null_operators
        check ((operator_type <> ALL
                (ARRAY ['IS_NULL'::predicate.operator_type, 'IS_NOT_NULL'::predicate.operator_type])) OR
               ((value_id IS NULL) AND (right_operand_id IS NULL))),
    constraint chk_attribute_exclusivity
        check ((meta_attribute_id IS NULL) OR (path_expression_id IS NULL)),
    constraint chk_comparison_operand
        check ((node_type <> 'COMPARISON_OPERATOR'::predicate.node_type) OR
               ((meta_attribute_id IS NOT NULL) OR (path_expression_id IS NOT NULL) OR (left_operand_id IS NOT NULL)))
);

comment on table predicate.predicate_node is 'Для geometry атрибутов с operator_type LT/GT - это DISTANCE_SPHERE операции';

alter table predicate.predicate_node
    owner to postgres;

create index idx_predicate_node_type
    on predicate.predicate_node (node_type);

create index idx_predicate_node_operator
    on predicate.predicate_node (operator_type);

create index idx_predicate_node_left
    on predicate.predicate_node (left_operand_id);

create index idx_predicate_node_right
    on predicate.predicate_node (right_operand_id);

create table predicate.predicate_node_value
(
    id                uuid default gen_random_uuid() not null
        primary key,
    string_value      varchar(1000),
    boolean_value     boolean,
    integer_value     integer,
    long_value        bigint,
    date_value        timestamp with time zone,
    timestamp_value   timestamp,
    enum_value_id     uuid
        constraint fk_enum_value_id
            references meta.meta_enum_value
            on delete set null,
    value_type        meta.basic_type                not null,
    predicate_node_id uuid
        constraint fk_node_value_node
            references predicate.predicate_node
            on delete set null,
    double_value      double precision,
    point_value       geometry(Point, 4326),
    constraint chk_value_type_string
        check ((value_type <> 'STRING'::meta.basic_type) OR (string_value IS NOT NULL)),
    constraint chk_value_type_boolean
        check ((value_type <> 'BOOLEAN'::meta.basic_type) OR (boolean_value IS NOT NULL)),
    constraint chk_value_type_integer
        check ((value_type <> 'INTEGER'::meta.basic_type) OR (integer_value IS NOT NULL)),
    constraint chk_value_type_long
        check ((value_type <> 'LONG'::meta.basic_type) OR (long_value IS NOT NULL)),
    constraint chk_value_type_date
        check ((value_type <> 'OFFSET_DATE_TIME'::meta.basic_type) OR (date_value IS NOT NULL)),
    constraint chk_value_type_timestamp
        check ((value_type <> 'TIMESTAMP'::meta.basic_type) OR (timestamp_value IS NOT NULL)),
    constraint chk_value_type_enum
        check ((value_type <> 'ENUM'::meta.basic_type) OR (enum_value_id IS NOT NULL)),
    constraint chk_value_type_double
        check ((value_type <> 'DOUBLE'::meta.basic_type) OR (double_value IS NOT NULL)),
    constraint chk_single_value
        check (((((((((((string_value IS NOT NULL))::integer + ((boolean_value IS NOT NULL))::integer) +
                      ((integer_value IS NOT NULL))::integer) + ((long_value IS NOT NULL))::integer) +
                    ((date_value IS NOT NULL))::integer) + ((timestamp_value IS NOT NULL))::integer) +
                  ((double_value IS NOT NULL))::integer) + ((point_value IS NOT NULL))::integer) +
                ((enum_value_id IS NOT NULL))::integer) = 1),
    constraint chk_value_type_point
        check ((value_type <> 'POINT'::meta.basic_type) OR (point_value IS NOT NULL))
);

comment on table predicate.predicate_node_value is 'Универсальное хранилище значений для предикатов';

comment on column predicate.predicate_node_value.double_value is 'Значение типа DOUBLE для предикатов';

alter table predicate.predicate_node_value
    owner to postgres;

alter table predicate.predicate_node
    add constraint fk_node_value
        foreign key (value_id) references predicate.predicate_node_value
            on delete cascade;

create index idx_predicate_value_type
    on predicate.predicate_node_value (value_type);

create table predicate.predicate_node_in_values
(
    predicate_node_id uuid    not null
        constraint fk_node_in_values_node
            references predicate.predicate_node
            on delete cascade,
    node_value_id     uuid    not null
        constraint fk_node_in_values_value
            references predicate.predicate_node_value
            on delete cascade,
    value_order       integer not null
        constraint chk_in_values_order_positive
            check (value_order >= 0),
    primary key (predicate_node_id, node_value_id)
);

comment on table predicate.predicate_node_in_values is 'Таблица для хранения множественных значений оператора IN';

alter table predicate.predicate_node_in_values
    owner to postgres;

create index idx_in_values_node
    on predicate.predicate_node_in_values (predicate_node_id);

create index idx_in_values_order
    on predicate.predicate_node_in_values (value_order);

create table predicate.predicate_definition
(
    id             uuid default gen_random_uuid() not null
        primary key,
    name           varchar(255)                   not null,
    meta_entity_id uuid                           not null
        constraint fk_predicate_meta_entity
            references meta.meta_entity
            on delete cascade,
    root_node_id   uuid
        constraint fk_predicate_root_node
            references predicate.predicate_node
            on delete set null
);

comment on table predicate.predicate_definition is 'Определение предиката - корневая сущность';

alter table predicate.predicate_definition
    owner to postgres;

create index idx_predicate_definition_entity
    on predicate.predicate_definition (meta_entity_id);

create index idx_predicate_definition_name
    on predicate.predicate_definition (name);

create table policy.permission
(
    id           uuid not null
        primary key,
    entity_id    uuid not null
        constraint fk_permission_entity_id
            references meta.meta_entity,
    attribute_id uuid not null
        constraint fk_permission_attribute_id
            references meta.meta_attribute,
    predicate_id uuid
        constraint fk_permission_predicate_id
            references predicate.predicate_definition
);

alter table policy.permission
    owner to postgres;

create index idx_permission_entity_id
    on policy.permission (entity_id);

create index idx_permission_attribute_id
    on policy.permission (attribute_id);

create index idx_permission_predicate_id
    on policy.permission (predicate_id);

create table policy.permission_user_roles
(
    permission_id uuid         not null
        constraint fk_permission_user_roles_permission_id
            references policy.permission,
    user_role     varchar(255) not null,
    primary key (permission_id, user_role)
);

alter table policy.permission_user_roles
    owner to postgres;

create index idx_permission_user_roles_permission_id
    on policy.permission_user_roles (permission_id);

-- Убираем поле meta_attribute_id из predicate_node
ALTER TABLE predicate.predicate_node DROP COLUMN meta_attribute_id;

-- Убираем поле target_attribute_id из predicate_path_expression
ALTER TABLE predicate.predicate_path_expression DROP COLUMN target_attribute_id;
