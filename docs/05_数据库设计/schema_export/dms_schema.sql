--
-- PostgreSQL database dump
--

\restrict ImBwzg8WA7vxbjRjUdTTlHHNqdUev5I2j0IDYvMh9lTgT2fPhXcQ6tw1lltyhyF

-- Dumped from database version 14.23
-- Dumped by pg_dump version 14.23

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: adjustment_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.adjustment_lines (
    id bigint NOT NULL,
    adjustment_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    qty numeric(14,4),
    reason text,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: adjustment_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.adjustment_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: adjustment_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.adjustment_lines_id_seq OWNED BY public.adjustment_lines.id;


--
-- Name: approval_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.approval_history (
    id bigint NOT NULL,
    task_id bigint,
    ref_type character varying(32),
    ref_id bigint,
    operator_id bigint,
    action character varying(16),
    comment text,
    at_time timestamp with time zone DEFAULT now()
);


--
-- Name: approval_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.approval_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: approval_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.approval_history_id_seq OWNED BY public.approval_history.id;


--
-- Name: approval_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.approval_tasks (
    id bigint NOT NULL,
    tenant_id uuid,
    workflow_id bigint,
    workflow_node_code character varying(64),
    ref_type character varying(32) NOT NULL,
    ref_id bigint NOT NULL,
    assignee_id bigint NOT NULL,
    status character varying(16) DEFAULT 'PENDING'::character varying,
    action character varying(16),
    comment text,
    deadline timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    done_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0
);


--
-- Name: approval_tasks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.approval_tasks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: approval_tasks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.approval_tasks_id_seq OWNED BY public.approval_tasks.id;


--
-- Name: async_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.async_jobs (
    id bigint NOT NULL,
    tenant_id uuid,
    user_id bigint,
    job_type character varying(32),
    payload jsonb,
    status character varying(16) DEFAULT 'PENDING'::character varying,
    progress integer DEFAULT 0,
    result jsonb,
    error text,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: async_jobs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.async_jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: async_jobs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.async_jobs_id_seq OWNED BY public.async_jobs.id;


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id bigint NOT NULL,
    tenant_id uuid,
    user_id bigint,
    action character varying(32),
    resource_type character varying(64),
    resource_id character varying(64),
    before jsonb,
    after jsonb,
    ip character varying(64),
    user_agent text,
    at_time timestamp with time zone DEFAULT now() NOT NULL
)
PARTITION BY RANGE (at_time);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_logs_id_seq OWNED BY public.audit_logs.id;


--
-- Name: audit_logs_2026_07; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs_2026_07 (
    id bigint DEFAULT nextval('public.audit_logs_id_seq'::regclass) NOT NULL,
    tenant_id uuid,
    user_id bigint,
    action character varying(32),
    resource_type character varying(64),
    resource_id character varying(64),
    before jsonb,
    after jsonb,
    ip character varying(64),
    user_agent text,
    at_time timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: audit_logs_2026_08; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs_2026_08 (
    id bigint DEFAULT nextval('public.audit_logs_id_seq'::regclass) NOT NULL,
    tenant_id uuid,
    user_id bigint,
    action character varying(32),
    resource_type character varying(64),
    resource_id character varying(64),
    before jsonb,
    after jsonb,
    ip character varying(64),
    user_agent text,
    at_time timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: audit_logs_2026_09; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs_2026_09 (
    id bigint DEFAULT nextval('public.audit_logs_id_seq'::regclass) NOT NULL,
    tenant_id uuid,
    user_id bigint,
    action character varying(32),
    resource_type character varying(64),
    resource_id character varying(64),
    before jsonb,
    after jsonb,
    ip character varying(64),
    user_agent text,
    at_time timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: audit_logs_2026_10; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs_2026_10 (
    id bigint DEFAULT nextval('public.audit_logs_id_seq'::regclass) NOT NULL,
    tenant_id uuid,
    user_id bigint,
    action character varying(32),
    resource_type character varying(64),
    resource_id character varying(64),
    before jsonb,
    after jsonb,
    ip character varying(64),
    user_agent text,
    at_time timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: audit_logs_2026_11; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs_2026_11 (
    id bigint DEFAULT nextval('public.audit_logs_id_seq'::regclass) NOT NULL,
    tenant_id uuid,
    user_id bigint,
    action character varying(32),
    resource_type character varying(64),
    resource_id character varying(64),
    before jsonb,
    after jsonb,
    ip character varying(64),
    user_agent text,
    at_time timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: audit_logs_2026_12; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs_2026_12 (
    id bigint DEFAULT nextval('public.audit_logs_id_seq'::regclass) NOT NULL,
    tenant_id uuid,
    user_id bigint,
    action character varying(32),
    resource_type character varying(64),
    resource_id character varying(64),
    before jsonb,
    after jsonb,
    ip character varying(64),
    user_agent text,
    at_time timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: authorizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.authorizations (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    contract_id bigint,
    auth_type character varying(32) NOT NULL,
    product_line_id bigint,
    product_id bigint,
    terminal_id bigint,
    region_id bigint,
    valid_from date NOT NULL,
    valid_to date NOT NULL,
    status character varying(16) DEFAULT 'active'::character varying,
    source character varying(16) DEFAULT 'contract'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: authorizations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.authorizations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: authorizations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.authorizations_id_seq OWNED BY public.authorizations.id;


--
-- Name: contract_applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contract_applications (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64) NOT NULL,
    application_type character varying(16) NOT NULL,
    contract_category character varying(32) NOT NULL,
    ref_contract_id bigint,
    dealer_id bigint,
    dealer_snapshot jsonb,
    authorization_scope jsonb,
    indicators jsonb,
    valid_from date,
    valid_to date,
    status character varying(16) DEFAULT 'draft'::character varying,
    created_by bigint,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    updated_by bigint,
    submitted_at timestamp with time zone,
    effective_at timestamp with time zone,
    remark text,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: contract_applications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contract_applications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: contract_applications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contract_applications_id_seq OWNED BY public.contract_applications.id;


--
-- Name: contract_attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contract_attachments (
    id bigint NOT NULL,
    tenant_id uuid,
    ref_type character varying(16),
    ref_id bigint,
    category character varying(64),
    file_id bigint,
    file_url text,
    file_name character varying(255),
    size_bytes bigint,
    uploaded_by bigint,
    uploaded_at timestamp with time zone DEFAULT now(),
    deleted_at timestamp with time zone
);


--
-- Name: contract_attachments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contract_attachments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: contract_attachments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contract_attachments_id_seq OWNED BY public.contract_attachments.id;


--
-- Name: contract_diff; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contract_diff (
    id bigint NOT NULL,
    application_id bigint,
    field_group character varying(64),
    field_key character varying(128),
    before_value text,
    after_value text,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: contract_diff_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contract_diff_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: contract_diff_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contract_diff_id_seq OWNED BY public.contract_diff.id;


--
-- Name: contract_signatures; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contract_signatures (
    id bigint NOT NULL,
    tenant_id uuid,
    contract_id bigint,
    signer_type character varying(16),
    signer_user_id bigint,
    ca_serial_no character varying(128),
    sms_code_ref character varying(64),
    signed_at timestamp with time zone DEFAULT now(),
    status character varying(16)
);


--
-- Name: contract_signatures_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contract_signatures_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: contract_signatures_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contract_signatures_id_seq OWNED BY public.contract_signatures.id;


--
-- Name: contract_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contract_templates (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(200),
    category character varying(32),
    content_url text,
    variables text[],
    version integer DEFAULT 1,
    status character varying(16) DEFAULT 'active'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    deleted_at timestamp with time zone
);


--
-- Name: contract_templates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contract_templates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: contract_templates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contract_templates_id_seq OWNED BY public.contract_templates.id;


--
-- Name: contracts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contracts (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64) NOT NULL,
    application_id bigint,
    dealer_id bigint,
    category character varying(32),
    valid_from date,
    valid_to date,
    status character varying(16) DEFAULT 'effective'::character varying,
    pdf_url text,
    ca_serial_no character varying(128),
    dealer_signed_at timestamp with time zone,
    vendor_signed_at timestamp with time zone,
    archived_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: contracts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contracts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: contracts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contracts_id_seq OWNED BY public.contracts.id;


--
-- Name: data_scopes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data_scopes (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    user_id bigint NOT NULL,
    scope_type character varying(16) NOT NULL,
    org_ids bigint[] DEFAULT ARRAY[]::bigint[],
    dealer_ids bigint[] DEFAULT ARRAY[]::bigint[],
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: data_scopes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.data_scopes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: data_scopes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.data_scopes_id_seq OWNED BY public.data_scopes.id;


--
-- Name: dealer_addresses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dealer_addresses (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    is_default boolean DEFAULT false,
    contact_name character varying(100),
    phone character varying(32),
    province character varying(64),
    city character varying(64),
    district character varying(64),
    address character varying(500),
    postal_code character varying(16),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: dealer_addresses_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dealer_addresses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dealer_addresses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dealer_addresses_id_seq OWNED BY public.dealer_addresses.id;


--
-- Name: dealer_kpi_snapshots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dealer_kpi_snapshots (
    id bigint NOT NULL,
    tenant_id uuid,
    dealer_id bigint,
    period_yyyymm character(6),
    stock_report_rate numeric(5,4),
    sales_report_rate numeric(5,4),
    order_pass_rate numeric(5,4),
    return_rate numeric(5,4),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: dealer_kpi_snapshots_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dealer_kpi_snapshots_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dealer_kpi_snapshots_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dealer_kpi_snapshots_id_seq OWNED BY public.dealer_kpi_snapshots.id;


--
-- Name: dealers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dealers (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(32) NOT NULL,
    name character varying(200) NOT NULL,
    level character varying(16),
    parent_dealer_id bigint,
    legal_person character varying(64),
    usc_no character varying(32),
    reg_address character varying(500),
    reg_capital numeric(18,2),
    founded_at date,
    business_scope character varying(500),
    gsp_status character varying(16),
    gsp_expire date,
    gmp_status character varying(16),
    gmp_expire date,
    region_id bigint,
    contact_name character varying(100),
    contact_phone character varying(32),
    contact_email character varying(128),
    sales_owner_user_id bigint,
    status character varying(16) DEFAULT 'active'::character varying,
    attrs jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone,
    extra jsonb DEFAULT '{}'::jsonb
);


--
-- Name: dealers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dealers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dealers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dealers_id_seq OWNED BY public.dealers.id;


--
-- Name: dict_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dict_items (
    id bigint NOT NULL,
    type_id bigint,
    code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    seq integer,
    status character varying(16) DEFAULT 'active'::character varying,
    attrs jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: dict_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dict_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dict_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dict_items_id_seq OWNED BY public.dict_items.id;


--
-- Name: dict_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dict_types (
    id bigint NOT NULL,
    tenant_id uuid,
    code character varying(64) NOT NULL,
    name character varying(200),
    description text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: dict_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dict_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dict_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dict_types_id_seq OWNED BY public.dict_types.id;


--
-- Name: distribution_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.distribution_lines (
    id bigint NOT NULL,
    shipment_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    qty numeric(14,4),
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: distribution_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.distribution_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: distribution_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.distribution_lines_id_seq OWNED BY public.distribution_lines.id;


--
-- Name: distribution_shipments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.distribution_shipments (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64),
    from_dealer_id bigint,
    to_dealer_id bigint,
    ref_order_id bigint,
    status character varying(16) DEFAULT 'PENDING'::character varying,
    express_no character varying(64),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: distribution_shipments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.distribution_shipments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: distribution_shipments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.distribution_shipments_id_seq OWNED BY public.distribution_shipments.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: form_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.form_configs (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    form_key character varying(64) NOT NULL,
    field_key character varying(64) NOT NULL,
    field_label character varying(100),
    field_type character varying(16) DEFAULT 'text'::character varying,
    is_native boolean DEFAULT true,
    required boolean DEFAULT false,
    show_in_list boolean DEFAULT true,
    show_in_form boolean DEFAULT true,
    show_in_detail boolean DEFAULT true,
    default_value text,
    options_json text,
    picker_resource character varying(64),
    placeholder character varying(200),
    field_group character varying(64),
    sort_order integer DEFAULT 100,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: form_configs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.form_configs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: form_configs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.form_configs_id_seq OWNED BY public.form_configs.id;


--
-- Name: hospitals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hospitals (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    type character varying(32),
    level character varying(32),
    region_id bigint,
    address character varying(500),
    contact character varying(100),
    phone character varying(32),
    attrs jsonb DEFAULT '{}'::jsonb,
    status character varying(16) DEFAULT 'active'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone,
    extra jsonb DEFAULT '{}'::jsonb
);


--
-- Name: hospitals_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hospitals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hospitals_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.hospitals_id_seq OWNED BY public.hospitals.id;


--
-- Name: inventory; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    warehouse_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    prod_date date,
    exp_date date,
    qty numeric(14,4) DEFAULT 0 NOT NULL,
    in_source character varying(32),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0
);


--
-- Name: inventory_adjustments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_adjustments (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64),
    dealer_id bigint,
    warehouse_id bigint,
    adj_category character varying(16),
    adj_type character varying(32),
    status character varying(16) DEFAULT 'DRAFT'::character varying,
    reason text,
    operator_id bigint,
    approver_id bigint,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: inventory_adjustments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventory_adjustments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_adjustments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_adjustments_id_seq OWNED BY public.inventory_adjustments.id;


--
-- Name: inventory_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventory_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_id_seq OWNED BY public.inventory.id;


--
-- Name: inventory_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_transactions (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    warehouse_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    qty_change numeric(14,4) NOT NULL,
    txn_type character varying(32) NOT NULL,
    ref_doc_type character varying(32),
    ref_doc_id bigint,
    at_time timestamp with time zone DEFAULT now() NOT NULL,
    operator_id bigint
)
PARTITION BY RANGE (at_time);


--
-- Name: inventory_transactions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventory_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_transactions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_transactions_id_seq OWNED BY public.inventory_transactions.id;


--
-- Name: inventory_transactions_2026_07; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_transactions_2026_07 (
    id bigint DEFAULT nextval('public.inventory_transactions_id_seq'::regclass) NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    warehouse_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    qty_change numeric(14,4) NOT NULL,
    txn_type character varying(32) NOT NULL,
    ref_doc_type character varying(32),
    ref_doc_id bigint,
    at_time timestamp with time zone DEFAULT now() NOT NULL,
    operator_id bigint
);


--
-- Name: inventory_transactions_2026_08; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_transactions_2026_08 (
    id bigint DEFAULT nextval('public.inventory_transactions_id_seq'::regclass) NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    warehouse_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    qty_change numeric(14,4) NOT NULL,
    txn_type character varying(32) NOT NULL,
    ref_doc_type character varying(32),
    ref_doc_id bigint,
    at_time timestamp with time zone DEFAULT now() NOT NULL,
    operator_id bigint
);


--
-- Name: inventory_transactions_2026_09; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_transactions_2026_09 (
    id bigint DEFAULT nextval('public.inventory_transactions_id_seq'::regclass) NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    warehouse_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    qty_change numeric(14,4) NOT NULL,
    txn_type character varying(32) NOT NULL,
    ref_doc_type character varying(32),
    ref_doc_id bigint,
    at_time timestamp with time zone DEFAULT now() NOT NULL,
    operator_id bigint
);


--
-- Name: inventory_transactions_2026_10; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_transactions_2026_10 (
    id bigint DEFAULT nextval('public.inventory_transactions_id_seq'::regclass) NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    warehouse_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    qty_change numeric(14,4) NOT NULL,
    txn_type character varying(32) NOT NULL,
    ref_doc_type character varying(32),
    ref_doc_id bigint,
    at_time timestamp with time zone DEFAULT now() NOT NULL,
    operator_id bigint
);


--
-- Name: inventory_transactions_2026_11; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_transactions_2026_11 (
    id bigint DEFAULT nextval('public.inventory_transactions_id_seq'::regclass) NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    warehouse_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    qty_change numeric(14,4) NOT NULL,
    txn_type character varying(32) NOT NULL,
    ref_doc_type character varying(32),
    ref_doc_id bigint,
    at_time timestamp with time zone DEFAULT now() NOT NULL,
    operator_id bigint
);


--
-- Name: inventory_transactions_2026_12; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_transactions_2026_12 (
    id bigint DEFAULT nextval('public.inventory_transactions_id_seq'::regclass) NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    warehouse_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    qty_change numeric(14,4) NOT NULL,
    txn_type character varying(32) NOT NULL,
    ref_doc_type character varying(32),
    ref_doc_id bigint,
    at_time timestamp with time zone DEFAULT now() NOT NULL,
    operator_id bigint
);


--
-- Name: loan_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loan_lines (
    id bigint NOT NULL,
    loan_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    qty numeric(14,4),
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: loan_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.loan_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: loan_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.loan_lines_id_seq OWNED BY public.loan_lines.id;


--
-- Name: loans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loans (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64),
    lender_dealer_id bigint,
    borrower_dealer_id bigint,
    status character varying(16) DEFAULT 'PENDING'::character varying,
    ref_loan_id bigint,
    reason text,
    created_at timestamp with time zone DEFAULT now(),
    completed_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: loans_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.loans_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: loans_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.loans_id_seq OWNED BY public.loans.id;


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id bigint NOT NULL,
    tenant_id uuid,
    user_id bigint NOT NULL,
    channel character varying(16),
    title character varying(200),
    body text,
    ref_type character varying(32),
    ref_id character varying(64),
    is_read boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notifications_id_seq OWNED BY public.notifications.id;


--
-- Name: order_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_lines (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    product_id bigint NOT NULL,
    qty numeric(14,4) NOT NULL,
    unit_price numeric(14,2) NOT NULL,
    tax_rate numeric(5,4),
    sub_total numeric(18,2) NOT NULL,
    is_gift boolean DEFAULT false,
    seq integer,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    extra jsonb DEFAULT '{}'::jsonb
);


--
-- Name: order_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.order_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.order_lines_id_seq OWNED BY public.order_lines.id;


--
-- Name: order_promotion_hits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_promotion_hits (
    id bigint NOT NULL,
    order_id bigint,
    promotion_id bigint NOT NULL,
    rule_type character varying(16),
    discount numeric(18,2),
    gift_lines jsonb,
    detail jsonb,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: order_promotion_hits_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.order_promotion_hits_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_promotion_hits_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.order_promotion_hits_id_seq OWNED BY public.order_promotion_hits.id;


--
-- Name: order_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_status_history (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    from_status character varying(32),
    to_status character varying(32),
    action character varying(32),
    operator_id bigint,
    comment text,
    at_time timestamp with time zone DEFAULT now()
);


--
-- Name: order_status_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.order_status_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_status_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.order_status_history_id_seq OWNED BY public.order_status_history.id;


--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64) NOT NULL,
    order_type character varying(16) NOT NULL,
    dealer_id bigint NOT NULL,
    ship_address_id bigint,
    ship_snapshot jsonb,
    status character varying(32) DEFAULT 'DRAFT'::character varying NOT NULL,
    parent_order_id bigint,
    amount_incl_tax numeric(18,2) DEFAULT 0,
    discount_amount numeric(18,2) DEFAULT 0,
    final_amount numeric(18,2) DEFAULT 0,
    remark text,
    expected_date date,
    submitted_at timestamp with time zone,
    approved_at timestamp with time zone,
    shipped_at timestamp with time zone,
    received_at timestamp with time zone,
    closed_at timestamp with time zone,
    erp_order_no character varying(64),
    created_by bigint,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone,
    extra jsonb DEFAULT '{}'::jsonb
);


--
-- Name: orders_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.orders_id_seq OWNED BY public.orders.id;


--
-- Name: org_units; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.org_units (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    parent_id bigint,
    code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    level integer NOT NULL,
    path character varying(500),
    type character varying(32),
    status character varying(16) DEFAULT 'active'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: org_units_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.org_units_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: org_units_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.org_units_id_seq OWNED BY public.org_units.id;


--
-- Name: price_lists; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.price_lists (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    product_id bigint,
    dealer_id bigint,
    price numeric(14,2) NOT NULL,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    valid_from date NOT NULL,
    valid_to date,
    source character varying(16) DEFAULT 'ERP'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: price_lists_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.price_lists_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: price_lists_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.price_lists_id_seq OWNED BY public.price_lists.id;


--
-- Name: product_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_categories (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    parent_id bigint,
    level integer NOT NULL,
    status character varying(16) DEFAULT 'active'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: product_categories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.product_categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: product_categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.product_categories_id_seq OWNED BY public.product_categories.id;


--
-- Name: products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.products (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64) NOT NULL,
    name_cn character varying(200) NOT NULL,
    name_en character varying(200),
    category_id bigint,
    spec character varying(100),
    unit character varying(32),
    current_price numeric(14,2),
    tax_rate numeric(5,4) DEFAULT 0.13,
    udi_required boolean DEFAULT true,
    warn_months integer DEFAULT 3,
    safety_qty numeric(14,4) DEFAULT 0,
    min_order_qty numeric(14,4),
    status character varying(16) DEFAULT 'active'::character varying,
    attrs jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone,
    extra jsonb DEFAULT '{}'::jsonb
);


--
-- Name: products_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: products_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.products_id_seq OWNED BY public.products.id;


--
-- Name: promotion_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.promotion_rules (
    id bigint NOT NULL,
    promotion_id bigint,
    seq integer,
    rule_detail jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: promotion_rules_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.promotion_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: promotion_rules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.promotion_rules_id_seq OWNED BY public.promotion_rules.id;


--
-- Name: promotion_status_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.promotion_status_logs (
    id bigint NOT NULL,
    promotion_id bigint,
    from_status character varying(16),
    to_status character varying(16),
    operator_id bigint,
    comment text,
    at_time timestamp with time zone DEFAULT now()
);


--
-- Name: promotion_status_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.promotion_status_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: promotion_status_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.promotion_status_logs_id_seq OWNED BY public.promotion_status_logs.id;


--
-- Name: promotions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.promotions (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64),
    name character varying(200) NOT NULL,
    promo_type character varying(16) NOT NULL,
    priority integer DEFAULT 50,
    valid_from timestamp with time zone,
    valid_to timestamp with time zone,
    dealer_scope jsonb,
    product_scope jsonb,
    exclusive boolean DEFAULT false,
    status character varying(16) DEFAULT 'draft'::character varying,
    description text,
    created_by bigint,
    approved_by bigint,
    approved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone,
    CONSTRAINT ck_promo_type_v1 CHECK (((promo_type)::text = ANY ((ARRAY['MOQ'::character varying, 'FULL_REDUCTION'::character varying, 'GIFT'::character varying, 'BUNDLE'::character varying])::text[])))
);


--
-- Name: promotions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.promotions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: promotions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.promotions_id_seq OWNED BY public.promotions.id;


--
-- Name: purchase_invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_invoices (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    ref_order_id bigint,
    invoice_no character varying(64) NOT NULL,
    invoice_date date,
    amount numeric(18,2),
    tax_amount numeric(18,2),
    tax_rate numeric(5,4),
    image_url text,
    uploaded_by bigint,
    uploaded_at timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: purchase_invoices_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.purchase_invoices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: purchase_invoices_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.purchase_invoices_id_seq OWNED BY public.purchase_invoices.id;


--
-- Name: purchase_order_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_order_lines (
    id bigint NOT NULL,
    po_id bigint,
    seq integer DEFAULT 1,
    product_id bigint,
    qty numeric(14,4) NOT NULL,
    received_qty numeric(14,4) DEFAULT 0,
    unit_price numeric(18,4),
    tax_rate numeric(5,4) DEFAULT 0.13,
    subtotal numeric(18,2),
    remark text,
    extra jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: purchase_order_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.purchase_order_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: purchase_order_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.purchase_order_lines_id_seq OWNED BY public.purchase_order_lines.id;


--
-- Name: purchase_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_orders (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64) NOT NULL,
    order_type character varying(16) DEFAULT 'NORMAL'::character varying,
    supplier_id bigint,
    supplier_name character varying(200),
    warehouse_id bigint,
    amount_incl_tax numeric(18,2) DEFAULT 0,
    discount_amount numeric(18,2) DEFAULT 0,
    final_amount numeric(18,2) DEFAULT 0,
    tax_amount numeric(18,2) DEFAULT 0,
    expected_date date,
    status character varying(16) DEFAULT 'DRAFT'::character varying,
    remark text,
    extra jsonb DEFAULT '{}'::jsonb,
    submitted_at timestamp with time zone,
    approved_at timestamp with time zone,
    approved_by bigint,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: purchase_orders_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.purchase_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: purchase_orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.purchase_orders_id_seq OWNED BY public.purchase_orders.id;


--
-- Name: rebate_previews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rebate_previews (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    period_yyyymm character(6),
    target_amount numeric(18,2),
    actual_amount numeric(18,2),
    achievement_rate numeric(9,4),
    tier_hit jsonb,
    gross_rebate numeric(18,2),
    deductions jsonb,
    net_rebate numeric(18,2),
    snapshot_at timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0
);


--
-- Name: rebate_previews_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rebate_previews_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rebate_previews_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rebate_previews_id_seq OWNED BY public.rebate_previews.id;


--
-- Name: rebate_settlements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rebate_settlements (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    period_yyyymm character(6),
    net_rebate numeric(18,2),
    status character varying(16) DEFAULT 'LOCKED'::character varying,
    settled_at timestamp with time zone DEFAULT now(),
    paid_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0
);


--
-- Name: rebate_settlements_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rebate_settlements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rebate_settlements_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rebate_settlements_id_seq OWNED BY public.rebate_settlements.id;


--
-- Name: receipt_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.receipt_lines (
    id bigint NOT NULL,
    receipt_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    prod_date date,
    exp_date date,
    expected_qty numeric(14,4),
    received_qty numeric(14,4),
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: receipt_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.receipt_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: receipt_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.receipt_lines_id_seq OWNED BY public.receipt_lines.id;


--
-- Name: receipts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.receipts (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64) NOT NULL,
    receipt_type character varying(16),
    ref_doc_type character varying(16),
    ref_doc_id bigint,
    dealer_id bigint,
    warehouse_id bigint,
    status character varying(16) DEFAULT 'PENDING'::character varying,
    received_at timestamp with time zone,
    received_by bigint,
    remark text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: receipts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.receipts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: receipts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.receipts_id_seq OWNED BY public.receipts.id;


--
-- Name: regions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.regions (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(32) NOT NULL,
    name character varying(100) NOT NULL,
    parent_id bigint,
    level integer NOT NULL,
    status character varying(16) DEFAULT 'active'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: regions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.regions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: regions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.regions_id_seq OWNED BY public.regions.id;


--
-- Name: resources; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.resources (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(200) NOT NULL,
    type character varying(16) NOT NULL,
    parent_id bigint,
    operations character varying(200)[] DEFAULT ARRAY[]::character varying[],
    path character varying(500),
    status character varying(16) DEFAULT 'active'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: resources_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.resources_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: resources_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.resources_id_seq OWNED BY public.resources.id;


--
-- Name: rma_authorizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rma_authorizations (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64),
    dealer_id bigint,
    product_line_id bigint,
    quota_amount numeric(18,2),
    quota_used numeric(18,2) DEFAULT 0,
    valid_from date,
    valid_to date,
    status character varying(16) DEFAULT 'active'::character varying,
    reason text,
    created_by bigint,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: rma_authorizations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rma_authorizations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rma_authorizations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rma_authorizations_id_seq OWNED BY public.rma_authorizations.id;


--
-- Name: rma_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rma_orders (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64),
    ref_rma_auth_id bigint,
    dealer_id bigint,
    rma_type character varying(16),
    amount numeric(18,2),
    status character varying(16) DEFAULT 'DRAFT'::character varying,
    lines jsonb,
    reason text,
    attachments jsonb,
    submitted_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: rma_orders_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rma_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rma_orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rma_orders_id_seq OWNED BY public.rma_orders.id;


--
-- Name: role_strategies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_strategies (
    role_id bigint NOT NULL,
    strategy_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    status character varying(16) DEFAULT 'active'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;


--
-- Name: sales_invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_invoices (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    ref_sales_out_id bigint,
    invoice_no character varying(64) NOT NULL,
    invoice_date date,
    amount numeric(18,2),
    tax_amount numeric(18,2),
    image_url text,
    uploaded_at timestamp with time zone DEFAULT now(),
    uploaded_by bigint,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: sales_invoices_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_invoices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_invoices_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_invoices_id_seq OWNED BY public.sales_invoices.id;


--
-- Name: sales_out_facts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_out_facts (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    product_id bigint,
    terminal_id bigint,
    region_id bigint,
    sales_date date,
    qty numeric(14,4),
    amount numeric(18,2),
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: sales_out_facts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_out_facts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_out_facts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_out_facts_id_seq OWNED BY public.sales_out_facts.id;


--
-- Name: sales_out_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_out_lines (
    id bigint NOT NULL,
    sales_out_id bigint,
    warehouse_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    qty numeric(14,4),
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: sales_out_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_out_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_out_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_out_lines_id_seq OWNED BY public.sales_out_lines.id;


--
-- Name: sales_outs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_outs (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64),
    dealer_id bigint,
    terminal_id bigint,
    business_type character varying(16),
    sales_date date,
    surgery_info jsonb,
    is_red boolean DEFAULT false,
    ref_sales_out_id bigint,
    status character varying(16) DEFAULT 'SUBMITTED'::character varying,
    amount_incl_tax numeric(18,2),
    created_by bigint,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: sales_outs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_outs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_outs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_outs_id_seq OWNED BY public.sales_outs.id;


--
-- Name: stock_move_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_move_lines (
    id bigint NOT NULL,
    move_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    qty numeric(14,4),
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: stock_move_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stock_move_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_move_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stock_move_lines_id_seq OWNED BY public.stock_move_lines.id;


--
-- Name: stock_moves; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_moves (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64),
    dealer_id bigint,
    src_warehouse_id bigint,
    dst_warehouse_id bigint,
    status character varying(16) DEFAULT 'COMPLETED'::character varying,
    reason text,
    operator_id bigint,
    at_time timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: stock_moves_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stock_moves_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_moves_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stock_moves_id_seq OWNED BY public.stock_moves.id;


--
-- Name: stocktake_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stocktake_lines (
    id bigint NOT NULL,
    stocktake_id bigint,
    product_id bigint,
    batch_no character varying(64),
    serial_no character varying(64),
    book_qty numeric(14,4),
    actual_qty numeric(14,4),
    diff_qty numeric(14,4),
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: stocktake_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stocktake_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stocktake_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stocktake_lines_id_seq OWNED BY public.stocktake_lines.id;


--
-- Name: stocktakes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stocktakes (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    period_yyyymm character(6) NOT NULL,
    uploaded_at timestamp with time zone,
    uploaded_by bigint,
    is_late boolean DEFAULT false,
    diff_summary jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: stocktakes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stocktakes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stocktakes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stocktakes_id_seq OWNED BY public.stocktakes.id;


--
-- Name: strategies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.strategies (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    status character varying(16) DEFAULT 'active'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: strategies_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.strategies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: strategies_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.strategies_id_seq OWNED BY public.strategies.id;


--
-- Name: strategy_resources; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.strategy_resources (
    strategy_id bigint NOT NULL,
    resource_id bigint NOT NULL,
    operations character varying(200)[] NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: system_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_settings (
    id bigint NOT NULL,
    scope character varying(16) NOT NULL,
    tenant_id uuid,
    key character varying(128) NOT NULL,
    value_json jsonb NOT NULL,
    description text,
    updated_by bigint,
    updated_at timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: system_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_settings_id_seq OWNED BY public.system_settings.id;


--
-- Name: temp_authorizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.temp_authorizations (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint,
    auth_type character varying(32),
    scope jsonb,
    valid_from date,
    valid_to date,
    reason text,
    status character varying(16) DEFAULT 'pending'::character varying,
    applicant_id bigint,
    approved_by bigint,
    approved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: temp_authorizations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.temp_authorizations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: temp_authorizations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.temp_authorizations_id_seq OWNED BY public.temp_authorizations.id;


--
-- Name: tenant_modules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_modules (
    tenant_id uuid NOT NULL,
    module_code character varying(32) NOT NULL,
    enabled boolean DEFAULT true,
    config jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: tenants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenants (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(32) NOT NULL,
    name character varying(200) NOT NULL,
    industry character varying(32) NOT NULL,
    timezone character varying(64) DEFAULT 'Asia/Shanghai'::character varying,
    logo_url text,
    status character varying(16) DEFAULT 'active'::character varying,
    modules_enabled jsonb DEFAULT '{}'::jsonb,
    quota jsonb DEFAULT '{}'::jsonb,
    attrs jsonb DEFAULT '{}'::jsonb,
    contact_name character varying(64),
    contact_email character varying(128),
    contact_phone character varying(32),
    effective_from date,
    effective_to date,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: COLUMN tenants.attrs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tenants.attrs IS 'attrs.primary_color 用于前端主题注入';


--
-- Name: user_login_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_login_logs (
    id bigint NOT NULL,
    tenant_id uuid,
    user_id bigint,
    login_type character varying(16),
    ip character varying(64),
    user_agent text,
    success boolean,
    fail_reason character varying(200),
    at_time timestamp with time zone DEFAULT now()
);


--
-- Name: user_login_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_login_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_login_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_login_logs_id_seq OWNED BY public.user_login_logs.id;


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    granted_at timestamp with time zone DEFAULT now(),
    granted_by bigint
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    username character varying(64) NOT NULL,
    name character varying(64) NOT NULL,
    user_type character varying(16) NOT NULL,
    password_hash character varying(255) NOT NULL,
    must_change_password boolean DEFAULT true,
    password_updated_at timestamp with time zone,
    email character varying(128),
    phone character varying(32),
    org_id bigint,
    dealer_id bigint,
    status character varying(16) DEFAULT 'active'::character varying,
    login_fail_count integer DEFAULT 0,
    locked_until timestamp with time zone,
    last_login_at timestamp with time zone,
    last_login_ip character varying(64),
    attrs jsonb DEFAULT '{}'::jsonb,
    wechat_openid character varying(64),
    wechat_unionid character varying(64),
    wechat_bound_at timestamp with time zone,
    sso_service_id character varying(64),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: warehouses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.warehouses (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    dealer_id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    type character varying(16) NOT NULL,
    hospital_id bigint,
    address character varying(500),
    status character varying(16) DEFAULT 'active'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    version integer DEFAULT 0,
    deleted_at timestamp with time zone,
    extra jsonb DEFAULT '{}'::jsonb
);


--
-- Name: warehouses_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.warehouses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: warehouses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.warehouses_id_seq OWNED BY public.warehouses.id;


--
-- Name: workflow_nodes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_nodes (
    id bigint NOT NULL,
    workflow_id bigint,
    code character varying(64) NOT NULL,
    name character varying(200),
    node_type character varying(16),
    assignee_strategy character varying(32),
    assignee_config jsonb,
    visible_fields text[],
    timeout_hours integer,
    seq integer,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: workflow_nodes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.workflow_nodes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: workflow_nodes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.workflow_nodes_id_seq OWNED BY public.workflow_nodes.id;


--
-- Name: workflows; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflows (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    version integer DEFAULT 1,
    status character varying(16) DEFAULT 'active'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    created_by bigint,
    updated_by bigint,
    deleted_at timestamp with time zone
);


--
-- Name: workflows_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.workflows_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: workflows_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.workflows_id_seq OWNED BY public.workflows.id;


--
-- Name: audit_logs_2026_07; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ATTACH PARTITION public.audit_logs_2026_07 FOR VALUES FROM ('2026-07-01 08:00:00+08') TO ('2026-08-01 08:00:00+08');


--
-- Name: audit_logs_2026_08; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ATTACH PARTITION public.audit_logs_2026_08 FOR VALUES FROM ('2026-08-01 08:00:00+08') TO ('2026-09-01 08:00:00+08');


--
-- Name: audit_logs_2026_09; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ATTACH PARTITION public.audit_logs_2026_09 FOR VALUES FROM ('2026-09-01 08:00:00+08') TO ('2026-10-01 08:00:00+08');


--
-- Name: audit_logs_2026_10; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ATTACH PARTITION public.audit_logs_2026_10 FOR VALUES FROM ('2026-10-01 08:00:00+08') TO ('2026-11-01 08:00:00+08');


--
-- Name: audit_logs_2026_11; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ATTACH PARTITION public.audit_logs_2026_11 FOR VALUES FROM ('2026-11-01 08:00:00+08') TO ('2026-12-01 08:00:00+08');


--
-- Name: audit_logs_2026_12; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ATTACH PARTITION public.audit_logs_2026_12 FOR VALUES FROM ('2026-12-01 08:00:00+08') TO ('2027-01-01 08:00:00+08');


--
-- Name: inventory_transactions_2026_07; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions ATTACH PARTITION public.inventory_transactions_2026_07 FOR VALUES FROM ('2026-07-01 08:00:00+08') TO ('2026-08-01 08:00:00+08');


--
-- Name: inventory_transactions_2026_08; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions ATTACH PARTITION public.inventory_transactions_2026_08 FOR VALUES FROM ('2026-08-01 08:00:00+08') TO ('2026-09-01 08:00:00+08');


--
-- Name: inventory_transactions_2026_09; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions ATTACH PARTITION public.inventory_transactions_2026_09 FOR VALUES FROM ('2026-09-01 08:00:00+08') TO ('2026-10-01 08:00:00+08');


--
-- Name: inventory_transactions_2026_10; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions ATTACH PARTITION public.inventory_transactions_2026_10 FOR VALUES FROM ('2026-10-01 08:00:00+08') TO ('2026-11-01 08:00:00+08');


--
-- Name: inventory_transactions_2026_11; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions ATTACH PARTITION public.inventory_transactions_2026_11 FOR VALUES FROM ('2026-11-01 08:00:00+08') TO ('2026-12-01 08:00:00+08');


--
-- Name: inventory_transactions_2026_12; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions ATTACH PARTITION public.inventory_transactions_2026_12 FOR VALUES FROM ('2026-12-01 08:00:00+08') TO ('2027-01-01 08:00:00+08');


--
-- Name: adjustment_lines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustment_lines ALTER COLUMN id SET DEFAULT nextval('public.adjustment_lines_id_seq'::regclass);


--
-- Name: approval_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approval_history ALTER COLUMN id SET DEFAULT nextval('public.approval_history_id_seq'::regclass);


--
-- Name: approval_tasks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approval_tasks ALTER COLUMN id SET DEFAULT nextval('public.approval_tasks_id_seq'::regclass);


--
-- Name: async_jobs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.async_jobs ALTER COLUMN id SET DEFAULT nextval('public.async_jobs_id_seq'::regclass);


--
-- Name: audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_logs_id_seq'::regclass);


--
-- Name: authorizations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.authorizations ALTER COLUMN id SET DEFAULT nextval('public.authorizations_id_seq'::regclass);


--
-- Name: contract_applications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_applications ALTER COLUMN id SET DEFAULT nextval('public.contract_applications_id_seq'::regclass);


--
-- Name: contract_attachments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_attachments ALTER COLUMN id SET DEFAULT nextval('public.contract_attachments_id_seq'::regclass);


--
-- Name: contract_diff id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_diff ALTER COLUMN id SET DEFAULT nextval('public.contract_diff_id_seq'::regclass);


--
-- Name: contract_signatures id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_signatures ALTER COLUMN id SET DEFAULT nextval('public.contract_signatures_id_seq'::regclass);


--
-- Name: contract_templates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_templates ALTER COLUMN id SET DEFAULT nextval('public.contract_templates_id_seq'::regclass);


--
-- Name: contracts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts ALTER COLUMN id SET DEFAULT nextval('public.contracts_id_seq'::regclass);


--
-- Name: data_scopes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_scopes ALTER COLUMN id SET DEFAULT nextval('public.data_scopes_id_seq'::regclass);


--
-- Name: dealer_addresses id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dealer_addresses ALTER COLUMN id SET DEFAULT nextval('public.dealer_addresses_id_seq'::regclass);


--
-- Name: dealer_kpi_snapshots id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dealer_kpi_snapshots ALTER COLUMN id SET DEFAULT nextval('public.dealer_kpi_snapshots_id_seq'::regclass);


--
-- Name: dealers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dealers ALTER COLUMN id SET DEFAULT nextval('public.dealers_id_seq'::regclass);


--
-- Name: dict_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dict_items ALTER COLUMN id SET DEFAULT nextval('public.dict_items_id_seq'::regclass);


--
-- Name: dict_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dict_types ALTER COLUMN id SET DEFAULT nextval('public.dict_types_id_seq'::regclass);


--
-- Name: distribution_lines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.distribution_lines ALTER COLUMN id SET DEFAULT nextval('public.distribution_lines_id_seq'::regclass);


--
-- Name: distribution_shipments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.distribution_shipments ALTER COLUMN id SET DEFAULT nextval('public.distribution_shipments_id_seq'::regclass);


--
-- Name: form_configs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.form_configs ALTER COLUMN id SET DEFAULT nextval('public.form_configs_id_seq'::regclass);


--
-- Name: hospitals id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospitals ALTER COLUMN id SET DEFAULT nextval('public.hospitals_id_seq'::regclass);


--
-- Name: inventory id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory ALTER COLUMN id SET DEFAULT nextval('public.inventory_id_seq'::regclass);


--
-- Name: inventory_adjustments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_adjustments ALTER COLUMN id SET DEFAULT nextval('public.inventory_adjustments_id_seq'::regclass);


--
-- Name: inventory_transactions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions ALTER COLUMN id SET DEFAULT nextval('public.inventory_transactions_id_seq'::regclass);


--
-- Name: loan_lines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_lines ALTER COLUMN id SET DEFAULT nextval('public.loan_lines_id_seq'::regclass);


--
-- Name: loans id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loans ALTER COLUMN id SET DEFAULT nextval('public.loans_id_seq'::regclass);


--
-- Name: notifications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications ALTER COLUMN id SET DEFAULT nextval('public.notifications_id_seq'::regclass);


--
-- Name: order_lines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_lines ALTER COLUMN id SET DEFAULT nextval('public.order_lines_id_seq'::regclass);


--
-- Name: order_promotion_hits id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_promotion_hits ALTER COLUMN id SET DEFAULT nextval('public.order_promotion_hits_id_seq'::regclass);


--
-- Name: order_status_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_status_history ALTER COLUMN id SET DEFAULT nextval('public.order_status_history_id_seq'::regclass);


--
-- Name: orders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders ALTER COLUMN id SET DEFAULT nextval('public.orders_id_seq'::regclass);


--
-- Name: org_units id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.org_units ALTER COLUMN id SET DEFAULT nextval('public.org_units_id_seq'::regclass);


--
-- Name: price_lists id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.price_lists ALTER COLUMN id SET DEFAULT nextval('public.price_lists_id_seq'::regclass);


--
-- Name: product_categories id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_categories ALTER COLUMN id SET DEFAULT nextval('public.product_categories_id_seq'::regclass);


--
-- Name: products id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products ALTER COLUMN id SET DEFAULT nextval('public.products_id_seq'::regclass);


--
-- Name: promotion_rules id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotion_rules ALTER COLUMN id SET DEFAULT nextval('public.promotion_rules_id_seq'::regclass);


--
-- Name: promotion_status_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotion_status_logs ALTER COLUMN id SET DEFAULT nextval('public.promotion_status_logs_id_seq'::regclass);


--
-- Name: promotions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotions ALTER COLUMN id SET DEFAULT nextval('public.promotions_id_seq'::regclass);


--
-- Name: purchase_invoices id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_invoices ALTER COLUMN id SET DEFAULT nextval('public.purchase_invoices_id_seq'::regclass);


--
-- Name: purchase_order_lines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_lines ALTER COLUMN id SET DEFAULT nextval('public.purchase_order_lines_id_seq'::regclass);


--
-- Name: purchase_orders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders ALTER COLUMN id SET DEFAULT nextval('public.purchase_orders_id_seq'::regclass);


--
-- Name: rebate_previews id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rebate_previews ALTER COLUMN id SET DEFAULT nextval('public.rebate_previews_id_seq'::regclass);


--
-- Name: rebate_settlements id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rebate_settlements ALTER COLUMN id SET DEFAULT nextval('public.rebate_settlements_id_seq'::regclass);


--
-- Name: receipt_lines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipt_lines ALTER COLUMN id SET DEFAULT nextval('public.receipt_lines_id_seq'::regclass);


--
-- Name: receipts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipts ALTER COLUMN id SET DEFAULT nextval('public.receipts_id_seq'::regclass);


--
-- Name: regions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regions ALTER COLUMN id SET DEFAULT nextval('public.regions_id_seq'::regclass);


--
-- Name: resources id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resources ALTER COLUMN id SET DEFAULT nextval('public.resources_id_seq'::regclass);


--
-- Name: rma_authorizations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rma_authorizations ALTER COLUMN id SET DEFAULT nextval('public.rma_authorizations_id_seq'::regclass);


--
-- Name: rma_orders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rma_orders ALTER COLUMN id SET DEFAULT nextval('public.rma_orders_id_seq'::regclass);


--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);


--
-- Name: sales_invoices id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_invoices ALTER COLUMN id SET DEFAULT nextval('public.sales_invoices_id_seq'::regclass);


--
-- Name: sales_out_facts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_out_facts ALTER COLUMN id SET DEFAULT nextval('public.sales_out_facts_id_seq'::regclass);


--
-- Name: sales_out_lines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_out_lines ALTER COLUMN id SET DEFAULT nextval('public.sales_out_lines_id_seq'::regclass);


--
-- Name: sales_outs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_outs ALTER COLUMN id SET DEFAULT nextval('public.sales_outs_id_seq'::regclass);


--
-- Name: stock_move_lines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_move_lines ALTER COLUMN id SET DEFAULT nextval('public.stock_move_lines_id_seq'::regclass);


--
-- Name: stock_moves id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_moves ALTER COLUMN id SET DEFAULT nextval('public.stock_moves_id_seq'::regclass);


--
-- Name: stocktake_lines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_lines ALTER COLUMN id SET DEFAULT nextval('public.stocktake_lines_id_seq'::regclass);


--
-- Name: stocktakes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktakes ALTER COLUMN id SET DEFAULT nextval('public.stocktakes_id_seq'::regclass);


--
-- Name: strategies id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.strategies ALTER COLUMN id SET DEFAULT nextval('public.strategies_id_seq'::regclass);


--
-- Name: system_settings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_settings ALTER COLUMN id SET DEFAULT nextval('public.system_settings_id_seq'::regclass);


--
-- Name: temp_authorizations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.temp_authorizations ALTER COLUMN id SET DEFAULT nextval('public.temp_authorizations_id_seq'::regclass);


--
-- Name: user_login_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_login_logs ALTER COLUMN id SET DEFAULT nextval('public.user_login_logs_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: warehouses id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses ALTER COLUMN id SET DEFAULT nextval('public.warehouses_id_seq'::regclass);


--
-- Name: workflow_nodes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_nodes ALTER COLUMN id SET DEFAULT nextval('public.workflow_nodes_id_seq'::regclass);


--
-- Name: workflows id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflows ALTER COLUMN id SET DEFAULT nextval('public.workflows_id_seq'::regclass);


--
-- Name: adjustment_lines adjustment_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustment_lines
    ADD CONSTRAINT adjustment_lines_pkey PRIMARY KEY (id);


--
-- Name: approval_history approval_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approval_history
    ADD CONSTRAINT approval_history_pkey PRIMARY KEY (id);


--
-- Name: approval_tasks approval_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approval_tasks
    ADD CONSTRAINT approval_tasks_pkey PRIMARY KEY (id);


--
-- Name: async_jobs async_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.async_jobs
    ADD CONSTRAINT async_jobs_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id, at_time);


--
-- Name: audit_logs_2026_07 audit_logs_2026_07_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs_2026_07
    ADD CONSTRAINT audit_logs_2026_07_pkey PRIMARY KEY (id, at_time);


--
-- Name: audit_logs_2026_08 audit_logs_2026_08_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs_2026_08
    ADD CONSTRAINT audit_logs_2026_08_pkey PRIMARY KEY (id, at_time);


--
-- Name: audit_logs_2026_09 audit_logs_2026_09_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs_2026_09
    ADD CONSTRAINT audit_logs_2026_09_pkey PRIMARY KEY (id, at_time);


--
-- Name: audit_logs_2026_10 audit_logs_2026_10_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs_2026_10
    ADD CONSTRAINT audit_logs_2026_10_pkey PRIMARY KEY (id, at_time);


--
-- Name: audit_logs_2026_11 audit_logs_2026_11_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs_2026_11
    ADD CONSTRAINT audit_logs_2026_11_pkey PRIMARY KEY (id, at_time);


--
-- Name: audit_logs_2026_12 audit_logs_2026_12_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs_2026_12
    ADD CONSTRAINT audit_logs_2026_12_pkey PRIMARY KEY (id, at_time);


--
-- Name: authorizations authorizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.authorizations
    ADD CONSTRAINT authorizations_pkey PRIMARY KEY (id);


--
-- Name: contract_applications contract_applications_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_applications
    ADD CONSTRAINT contract_applications_code_key UNIQUE (code);


--
-- Name: contract_applications contract_applications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_applications
    ADD CONSTRAINT contract_applications_pkey PRIMARY KEY (id);


--
-- Name: contract_attachments contract_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_attachments
    ADD CONSTRAINT contract_attachments_pkey PRIMARY KEY (id);


--
-- Name: contract_diff contract_diff_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_diff
    ADD CONSTRAINT contract_diff_pkey PRIMARY KEY (id);


--
-- Name: contract_signatures contract_signatures_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_signatures
    ADD CONSTRAINT contract_signatures_pkey PRIMARY KEY (id);


--
-- Name: contract_templates contract_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_templates
    ADD CONSTRAINT contract_templates_pkey PRIMARY KEY (id);


--
-- Name: contract_templates contract_templates_tenant_id_code_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_templates
    ADD CONSTRAINT contract_templates_tenant_id_code_version_key UNIQUE (tenant_id, code, version);


--
-- Name: contracts contracts_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_code_key UNIQUE (code);


--
-- Name: contracts contracts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_pkey PRIMARY KEY (id);


--
-- Name: data_scopes data_scopes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_scopes
    ADD CONSTRAINT data_scopes_pkey PRIMARY KEY (id);


--
-- Name: dealer_addresses dealer_addresses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dealer_addresses
    ADD CONSTRAINT dealer_addresses_pkey PRIMARY KEY (id);


--
-- Name: dealer_kpi_snapshots dealer_kpi_snapshots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dealer_kpi_snapshots
    ADD CONSTRAINT dealer_kpi_snapshots_pkey PRIMARY KEY (id);


--
-- Name: dealer_kpi_snapshots dealer_kpi_snapshots_tenant_id_dealer_id_period_yyyymm_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dealer_kpi_snapshots
    ADD CONSTRAINT dealer_kpi_snapshots_tenant_id_dealer_id_period_yyyymm_key UNIQUE (tenant_id, dealer_id, period_yyyymm);


--
-- Name: dealers dealers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dealers
    ADD CONSTRAINT dealers_pkey PRIMARY KEY (id);


--
-- Name: dealers dealers_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dealers
    ADD CONSTRAINT dealers_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: dict_items dict_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dict_items
    ADD CONSTRAINT dict_items_pkey PRIMARY KEY (id);


--
-- Name: dict_items dict_items_type_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dict_items
    ADD CONSTRAINT dict_items_type_id_code_key UNIQUE (type_id, code);


--
-- Name: dict_types dict_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dict_types
    ADD CONSTRAINT dict_types_pkey PRIMARY KEY (id);


--
-- Name: dict_types dict_types_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dict_types
    ADD CONSTRAINT dict_types_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: distribution_lines distribution_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.distribution_lines
    ADD CONSTRAINT distribution_lines_pkey PRIMARY KEY (id);


--
-- Name: distribution_shipments distribution_shipments_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.distribution_shipments
    ADD CONSTRAINT distribution_shipments_code_key UNIQUE (code);


--
-- Name: distribution_shipments distribution_shipments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.distribution_shipments
    ADD CONSTRAINT distribution_shipments_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: form_configs form_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.form_configs
    ADD CONSTRAINT form_configs_pkey PRIMARY KEY (id);


--
-- Name: form_configs form_configs_tenant_id_form_key_field_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.form_configs
    ADD CONSTRAINT form_configs_tenant_id_form_key_field_key_key UNIQUE (tenant_id, form_key, field_key);


--
-- Name: hospitals hospitals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospitals
    ADD CONSTRAINT hospitals_pkey PRIMARY KEY (id);


--
-- Name: hospitals hospitals_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospitals
    ADD CONSTRAINT hospitals_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: inventory_adjustments inventory_adjustments_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_adjustments
    ADD CONSTRAINT inventory_adjustments_code_key UNIQUE (code);


--
-- Name: inventory_adjustments inventory_adjustments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_adjustments
    ADD CONSTRAINT inventory_adjustments_pkey PRIMARY KEY (id);


--
-- Name: inventory inventory_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_pkey PRIMARY KEY (id);


--
-- Name: inventory inventory_tenant_id_warehouse_id_product_id_batch_no_serial_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_tenant_id_warehouse_id_product_id_batch_no_serial_key UNIQUE (tenant_id, warehouse_id, product_id, batch_no, serial_no);


--
-- Name: inventory_transactions inventory_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT inventory_transactions_pkey PRIMARY KEY (id, at_time);


--
-- Name: inventory_transactions_2026_07 inventory_transactions_2026_07_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions_2026_07
    ADD CONSTRAINT inventory_transactions_2026_07_pkey PRIMARY KEY (id, at_time);


--
-- Name: inventory_transactions_2026_08 inventory_transactions_2026_08_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions_2026_08
    ADD CONSTRAINT inventory_transactions_2026_08_pkey PRIMARY KEY (id, at_time);


--
-- Name: inventory_transactions_2026_09 inventory_transactions_2026_09_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions_2026_09
    ADD CONSTRAINT inventory_transactions_2026_09_pkey PRIMARY KEY (id, at_time);


--
-- Name: inventory_transactions_2026_10 inventory_transactions_2026_10_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions_2026_10
    ADD CONSTRAINT inventory_transactions_2026_10_pkey PRIMARY KEY (id, at_time);


--
-- Name: inventory_transactions_2026_11 inventory_transactions_2026_11_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions_2026_11
    ADD CONSTRAINT inventory_transactions_2026_11_pkey PRIMARY KEY (id, at_time);


--
-- Name: inventory_transactions_2026_12 inventory_transactions_2026_12_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions_2026_12
    ADD CONSTRAINT inventory_transactions_2026_12_pkey PRIMARY KEY (id, at_time);


--
-- Name: loan_lines loan_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_lines
    ADD CONSTRAINT loan_lines_pkey PRIMARY KEY (id);


--
-- Name: loans loans_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loans
    ADD CONSTRAINT loans_code_key UNIQUE (code);


--
-- Name: loans loans_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loans
    ADD CONSTRAINT loans_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: order_lines order_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_lines
    ADD CONSTRAINT order_lines_pkey PRIMARY KEY (id);


--
-- Name: order_promotion_hits order_promotion_hits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_promotion_hits
    ADD CONSTRAINT order_promotion_hits_pkey PRIMARY KEY (id);


--
-- Name: order_status_history order_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_status_history
    ADD CONSTRAINT order_status_history_pkey PRIMARY KEY (id);


--
-- Name: orders orders_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_code_key UNIQUE (code);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- Name: org_units org_units_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.org_units
    ADD CONSTRAINT org_units_pkey PRIMARY KEY (id);


--
-- Name: org_units org_units_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.org_units
    ADD CONSTRAINT org_units_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: price_lists price_lists_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.price_lists
    ADD CONSTRAINT price_lists_pkey PRIMARY KEY (id);


--
-- Name: product_categories product_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_categories
    ADD CONSTRAINT product_categories_pkey PRIMARY KEY (id);


--
-- Name: product_categories product_categories_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_categories
    ADD CONSTRAINT product_categories_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);


--
-- Name: products products_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: promotion_rules promotion_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotion_rules
    ADD CONSTRAINT promotion_rules_pkey PRIMARY KEY (id);


--
-- Name: promotion_status_logs promotion_status_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotion_status_logs
    ADD CONSTRAINT promotion_status_logs_pkey PRIMARY KEY (id);


--
-- Name: promotions promotions_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotions
    ADD CONSTRAINT promotions_code_key UNIQUE (code);


--
-- Name: promotions promotions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotions
    ADD CONSTRAINT promotions_pkey PRIMARY KEY (id);


--
-- Name: purchase_invoices purchase_invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_invoices
    ADD CONSTRAINT purchase_invoices_pkey PRIMARY KEY (id);


--
-- Name: purchase_invoices purchase_invoices_tenant_id_invoice_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_invoices
    ADD CONSTRAINT purchase_invoices_tenant_id_invoice_no_key UNIQUE (tenant_id, invoice_no);


--
-- Name: purchase_order_lines purchase_order_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_lines
    ADD CONSTRAINT purchase_order_lines_pkey PRIMARY KEY (id);


--
-- Name: purchase_orders purchase_orders_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_code_key UNIQUE (code);


--
-- Name: purchase_orders purchase_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_pkey PRIMARY KEY (id);


--
-- Name: rebate_previews rebate_previews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rebate_previews
    ADD CONSTRAINT rebate_previews_pkey PRIMARY KEY (id);


--
-- Name: rebate_previews rebate_previews_tenant_id_dealer_id_period_yyyymm_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rebate_previews
    ADD CONSTRAINT rebate_previews_tenant_id_dealer_id_period_yyyymm_key UNIQUE (tenant_id, dealer_id, period_yyyymm);


--
-- Name: rebate_settlements rebate_settlements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rebate_settlements
    ADD CONSTRAINT rebate_settlements_pkey PRIMARY KEY (id);


--
-- Name: rebate_settlements rebate_settlements_tenant_id_dealer_id_period_yyyymm_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rebate_settlements
    ADD CONSTRAINT rebate_settlements_tenant_id_dealer_id_period_yyyymm_key UNIQUE (tenant_id, dealer_id, period_yyyymm);


--
-- Name: receipt_lines receipt_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipt_lines
    ADD CONSTRAINT receipt_lines_pkey PRIMARY KEY (id);


--
-- Name: receipts receipts_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipts
    ADD CONSTRAINT receipts_code_key UNIQUE (code);


--
-- Name: receipts receipts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipts
    ADD CONSTRAINT receipts_pkey PRIMARY KEY (id);


--
-- Name: regions regions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regions
    ADD CONSTRAINT regions_pkey PRIMARY KEY (id);


--
-- Name: regions regions_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regions
    ADD CONSTRAINT regions_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: resources resources_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resources
    ADD CONSTRAINT resources_pkey PRIMARY KEY (id);


--
-- Name: resources resources_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resources
    ADD CONSTRAINT resources_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: rma_authorizations rma_authorizations_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rma_authorizations
    ADD CONSTRAINT rma_authorizations_code_key UNIQUE (code);


--
-- Name: rma_authorizations rma_authorizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rma_authorizations
    ADD CONSTRAINT rma_authorizations_pkey PRIMARY KEY (id);


--
-- Name: rma_orders rma_orders_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rma_orders
    ADD CONSTRAINT rma_orders_code_key UNIQUE (code);


--
-- Name: rma_orders rma_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rma_orders
    ADD CONSTRAINT rma_orders_pkey PRIMARY KEY (id);


--
-- Name: role_strategies role_strategies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_strategies
    ADD CONSTRAINT role_strategies_pkey PRIMARY KEY (role_id, strategy_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: roles roles_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: sales_invoices sales_invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_invoices
    ADD CONSTRAINT sales_invoices_pkey PRIMARY KEY (id);


--
-- Name: sales_invoices sales_invoices_tenant_id_invoice_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_invoices
    ADD CONSTRAINT sales_invoices_tenant_id_invoice_no_key UNIQUE (tenant_id, invoice_no);


--
-- Name: sales_out_facts sales_out_facts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_out_facts
    ADD CONSTRAINT sales_out_facts_pkey PRIMARY KEY (id);


--
-- Name: sales_out_lines sales_out_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_out_lines
    ADD CONSTRAINT sales_out_lines_pkey PRIMARY KEY (id);


--
-- Name: sales_outs sales_outs_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_outs
    ADD CONSTRAINT sales_outs_code_key UNIQUE (code);


--
-- Name: sales_outs sales_outs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_outs
    ADD CONSTRAINT sales_outs_pkey PRIMARY KEY (id);


--
-- Name: stock_move_lines stock_move_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_move_lines
    ADD CONSTRAINT stock_move_lines_pkey PRIMARY KEY (id);


--
-- Name: stock_moves stock_moves_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_moves
    ADD CONSTRAINT stock_moves_code_key UNIQUE (code);


--
-- Name: stock_moves stock_moves_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_moves
    ADD CONSTRAINT stock_moves_pkey PRIMARY KEY (id);


--
-- Name: stocktake_lines stocktake_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_lines
    ADD CONSTRAINT stocktake_lines_pkey PRIMARY KEY (id);


--
-- Name: stocktakes stocktakes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktakes
    ADD CONSTRAINT stocktakes_pkey PRIMARY KEY (id);


--
-- Name: stocktakes stocktakes_tenant_id_dealer_id_period_yyyymm_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktakes
    ADD CONSTRAINT stocktakes_tenant_id_dealer_id_period_yyyymm_key UNIQUE (tenant_id, dealer_id, period_yyyymm);


--
-- Name: strategies strategies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.strategies
    ADD CONSTRAINT strategies_pkey PRIMARY KEY (id);


--
-- Name: strategy_resources strategy_resources_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.strategy_resources
    ADD CONSTRAINT strategy_resources_pkey PRIMARY KEY (strategy_id, resource_id);


--
-- Name: system_settings system_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_settings
    ADD CONSTRAINT system_settings_pkey PRIMARY KEY (id);


--
-- Name: system_settings system_settings_scope_tenant_id_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_settings
    ADD CONSTRAINT system_settings_scope_tenant_id_key_key UNIQUE (scope, tenant_id, key);


--
-- Name: temp_authorizations temp_authorizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.temp_authorizations
    ADD CONSTRAINT temp_authorizations_pkey PRIMARY KEY (id);


--
-- Name: tenant_modules tenant_modules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_modules
    ADD CONSTRAINT tenant_modules_pkey PRIMARY KEY (tenant_id, module_code);


--
-- Name: tenants tenants_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_code_key UNIQUE (code);


--
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);


--
-- Name: user_login_logs user_login_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_login_logs
    ADD CONSTRAINT user_login_logs_pkey PRIMARY KEY (id);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_tenant_id_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_tenant_id_username_key UNIQUE (tenant_id, username);


--
-- Name: warehouses warehouses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_pkey PRIMARY KEY (id);


--
-- Name: warehouses warehouses_tenant_id_dealer_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_tenant_id_dealer_id_code_key UNIQUE (tenant_id, dealer_id, code);


--
-- Name: workflow_nodes workflow_nodes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_nodes
    ADD CONSTRAINT workflow_nodes_pkey PRIMARY KEY (id);


--
-- Name: workflows workflows_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflows
    ADD CONSTRAINT workflows_pkey PRIMARY KEY (id);


--
-- Name: workflows workflows_tenant_id_code_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflows
    ADD CONSTRAINT workflows_tenant_id_code_version_key UNIQUE (tenant_id, code, version);


--
-- Name: idx_audit_resource; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_resource ON ONLY public.audit_logs USING btree (resource_type, resource_id);


--
-- Name: audit_logs_2026_07_resource_type_resource_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_07_resource_type_resource_id_idx ON public.audit_logs_2026_07 USING btree (resource_type, resource_id);


--
-- Name: idx_audit_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_tenant_time ON ONLY public.audit_logs USING btree (tenant_id, at_time DESC);


--
-- Name: audit_logs_2026_07_tenant_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_07_tenant_id_at_time_idx ON public.audit_logs_2026_07 USING btree (tenant_id, at_time DESC);


--
-- Name: idx_audit_user_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_user_time ON ONLY public.audit_logs USING btree (user_id, at_time DESC);


--
-- Name: audit_logs_2026_07_user_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_07_user_id_at_time_idx ON public.audit_logs_2026_07 USING btree (user_id, at_time DESC);


--
-- Name: audit_logs_2026_08_resource_type_resource_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_08_resource_type_resource_id_idx ON public.audit_logs_2026_08 USING btree (resource_type, resource_id);


--
-- Name: audit_logs_2026_08_tenant_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_08_tenant_id_at_time_idx ON public.audit_logs_2026_08 USING btree (tenant_id, at_time DESC);


--
-- Name: audit_logs_2026_08_user_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_08_user_id_at_time_idx ON public.audit_logs_2026_08 USING btree (user_id, at_time DESC);


--
-- Name: audit_logs_2026_09_resource_type_resource_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_09_resource_type_resource_id_idx ON public.audit_logs_2026_09 USING btree (resource_type, resource_id);


--
-- Name: audit_logs_2026_09_tenant_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_09_tenant_id_at_time_idx ON public.audit_logs_2026_09 USING btree (tenant_id, at_time DESC);


--
-- Name: audit_logs_2026_09_user_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_09_user_id_at_time_idx ON public.audit_logs_2026_09 USING btree (user_id, at_time DESC);


--
-- Name: audit_logs_2026_10_resource_type_resource_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_10_resource_type_resource_id_idx ON public.audit_logs_2026_10 USING btree (resource_type, resource_id);


--
-- Name: audit_logs_2026_10_tenant_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_10_tenant_id_at_time_idx ON public.audit_logs_2026_10 USING btree (tenant_id, at_time DESC);


--
-- Name: audit_logs_2026_10_user_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_10_user_id_at_time_idx ON public.audit_logs_2026_10 USING btree (user_id, at_time DESC);


--
-- Name: audit_logs_2026_11_resource_type_resource_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_11_resource_type_resource_id_idx ON public.audit_logs_2026_11 USING btree (resource_type, resource_id);


--
-- Name: audit_logs_2026_11_tenant_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_11_tenant_id_at_time_idx ON public.audit_logs_2026_11 USING btree (tenant_id, at_time DESC);


--
-- Name: audit_logs_2026_11_user_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_11_user_id_at_time_idx ON public.audit_logs_2026_11 USING btree (user_id, at_time DESC);


--
-- Name: audit_logs_2026_12_resource_type_resource_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_12_resource_type_resource_id_idx ON public.audit_logs_2026_12 USING btree (resource_type, resource_id);


--
-- Name: audit_logs_2026_12_tenant_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_12_tenant_id_at_time_idx ON public.audit_logs_2026_12 USING btree (tenant_id, at_time DESC);


--
-- Name: audit_logs_2026_12_user_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_logs_2026_12_user_id_at_time_idx ON public.audit_logs_2026_12 USING btree (user_id, at_time DESC);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_adj_lines_adj; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_adj_lines_adj ON public.adjustment_lines USING btree (adjustment_id);


--
-- Name: idx_appr_assignee; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appr_assignee ON public.approval_tasks USING btree (assignee_id, status);


--
-- Name: idx_appr_hist_ref; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appr_hist_ref ON public.approval_history USING btree (ref_type, ref_id, at_time DESC);


--
-- Name: idx_appr_ref; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appr_ref ON public.approval_tasks USING btree (ref_type, ref_id);


--
-- Name: idx_async_jobs_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_async_jobs_status ON public.async_jobs USING btree (status);


--
-- Name: idx_async_jobs_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_async_jobs_user ON public.async_jobs USING btree (user_id, created_at DESC);


--
-- Name: idx_auth_contract; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_contract ON public.authorizations USING btree (contract_id);


--
-- Name: idx_auth_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_lookup ON public.authorizations USING btree (tenant_id, dealer_id, auth_type, status, valid_from, valid_to);


--
-- Name: idx_contract_app_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contract_app_dealer ON public.contract_applications USING btree (dealer_id);


--
-- Name: idx_contract_app_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contract_app_status ON public.contract_applications USING btree (tenant_id, status);


--
-- Name: idx_contract_att_ref; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contract_att_ref ON public.contract_attachments USING btree (ref_type, ref_id);


--
-- Name: idx_contract_diff_app; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contract_diff_app ON public.contract_diff USING btree (application_id);


--
-- Name: idx_contract_sig_contract; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contract_sig_contract ON public.contract_signatures USING btree (contract_id);


--
-- Name: idx_contracts_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contracts_dealer ON public.contracts USING btree (dealer_id);


--
-- Name: idx_contracts_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contracts_status ON public.contracts USING btree (tenant_id, status);


--
-- Name: idx_contracts_valid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contracts_valid ON public.contracts USING btree (tenant_id, valid_from, valid_to);


--
-- Name: idx_data_scopes_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_data_scopes_user ON public.data_scopes USING btree (tenant_id, user_id);


--
-- Name: idx_dealer_addr_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dealer_addr_dealer ON public.dealer_addresses USING btree (dealer_id);


--
-- Name: idx_dealers_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dealers_parent ON public.dealers USING btree (parent_dealer_id);


--
-- Name: idx_dealers_region; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dealers_region ON public.dealers USING btree (region_id);


--
-- Name: idx_dealers_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dealers_status ON public.dealers USING btree (tenant_id, status);


--
-- Name: idx_dist_from; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dist_from ON public.distribution_shipments USING btree (from_dealer_id);


--
-- Name: idx_dist_lines_shp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dist_lines_shp ON public.distribution_lines USING btree (shipment_id);


--
-- Name: idx_dist_to; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dist_to ON public.distribution_shipments USING btree (to_dealer_id);


--
-- Name: idx_fc_form; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fc_form ON public.form_configs USING btree (tenant_id, form_key, sort_order);


--
-- Name: idx_hospitals_region; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hospitals_region ON public.hospitals USING btree (region_id);


--
-- Name: idx_inv_adj_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_adj_dealer ON public.inventory_adjustments USING btree (dealer_id);


--
-- Name: idx_inv_adj_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_adj_status ON public.inventory_adjustments USING btree (tenant_id, status);


--
-- Name: idx_inv_expire; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_expire ON public.inventory USING btree (exp_date);


--
-- Name: idx_inv_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_lookup ON public.inventory USING btree (tenant_id, dealer_id, product_id, batch_no);


--
-- Name: idx_inv_txn_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_txn_product ON ONLY public.inventory_transactions USING btree (product_id, at_time DESC);


--
-- Name: idx_inv_txn_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_txn_tenant_time ON ONLY public.inventory_transactions USING btree (tenant_id, at_time DESC);


--
-- Name: idx_loan_lines_loan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loan_lines_loan ON public.loan_lines USING btree (loan_id);


--
-- Name: idx_loans_borrower; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loans_borrower ON public.loans USING btree (borrower_dealer_id);


--
-- Name: idx_loans_lender; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_loans_lender ON public.loans USING btree (lender_dealer_id);


--
-- Name: idx_login_logs_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_logs_tenant ON public.user_login_logs USING btree (tenant_id, at_time DESC);


--
-- Name: idx_login_logs_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_logs_user ON public.user_login_logs USING btree (user_id, at_time DESC);


--
-- Name: idx_noti_user_unread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_noti_user_unread ON public.notifications USING btree (user_id, is_read);


--
-- Name: idx_order_lines_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_lines_order ON public.order_lines USING btree (order_id);


--
-- Name: idx_order_lines_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_lines_product ON public.order_lines USING btree (product_id);


--
-- Name: idx_order_promo_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_promo_order ON public.order_promotion_hits USING btree (order_id);


--
-- Name: idx_order_status_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_status_order ON public.order_status_history USING btree (order_id, at_time DESC);


--
-- Name: idx_orders_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_dealer ON public.orders USING btree (dealer_id);


--
-- Name: idx_orders_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_status ON public.orders USING btree (tenant_id, status);


--
-- Name: idx_orders_submitted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_submitted ON public.orders USING btree (tenant_id, submitted_at DESC);


--
-- Name: idx_org_units_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_org_units_parent ON public.org_units USING btree (parent_id);


--
-- Name: idx_org_units_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_org_units_tenant ON public.org_units USING btree (tenant_id);


--
-- Name: idx_po_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_po_created ON public.purchase_orders USING btree (tenant_id, created_at DESC);


--
-- Name: idx_po_supp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_po_supp ON public.purchase_orders USING btree (supplier_id);


--
-- Name: idx_po_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_po_tenant ON public.purchase_orders USING btree (tenant_id, status);


--
-- Name: idx_pol_po; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pol_po ON public.purchase_order_lines USING btree (po_id);


--
-- Name: idx_price_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_price_active ON public.price_lists USING btree (tenant_id, product_id, dealer_id, valid_from DESC);


--
-- Name: idx_prod_cat_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_prod_cat_parent ON public.product_categories USING btree (parent_id);


--
-- Name: idx_products_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_category ON public.products USING btree (category_id);


--
-- Name: idx_products_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_tenant ON public.products USING btree (tenant_id, status);


--
-- Name: idx_promo_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promo_active ON public.promotions USING btree (tenant_id, status, valid_from, valid_to);


--
-- Name: idx_promo_log_promo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promo_log_promo ON public.promotion_status_logs USING btree (promotion_id, at_time DESC);


--
-- Name: idx_promo_rules_promo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promo_rules_promo ON public.promotion_rules USING btree (promotion_id, seq);


--
-- Name: idx_rcpt_lines_receipt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rcpt_lines_receipt ON public.receipt_lines USING btree (receipt_id);


--
-- Name: idx_receipts_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_receipts_dealer ON public.receipts USING btree (dealer_id);


--
-- Name: idx_receipts_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_receipts_status ON public.receipts USING btree (tenant_id, status);


--
-- Name: idx_regions_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_regions_parent ON public.regions USING btree (parent_id);


--
-- Name: idx_resources_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_resources_parent ON public.resources USING btree (parent_id);


--
-- Name: idx_rma_auth_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rma_auth_dealer ON public.rma_authorizations USING btree (dealer_id);


--
-- Name: idx_rma_orders_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rma_orders_dealer ON public.rma_orders USING btree (dealer_id, status);


--
-- Name: idx_sales_lines_out; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_lines_out ON public.sales_out_lines USING btree (sales_out_id);


--
-- Name: idx_sales_outs_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_outs_dealer ON public.sales_outs USING btree (dealer_id, sales_date DESC);


--
-- Name: idx_sales_outs_terminal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_outs_terminal ON public.sales_outs USING btree (terminal_id);


--
-- Name: idx_sof_dealer_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sof_dealer_date ON public.sales_out_facts USING btree (dealer_id, sales_date);


--
-- Name: idx_sof_tenant_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sof_tenant_date ON public.sales_out_facts USING btree (tenant_id, sales_date);


--
-- Name: idx_stkt_lines_stkt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stkt_lines_stkt ON public.stocktake_lines USING btree (stocktake_id);


--
-- Name: idx_stock_move_lines_move; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_move_lines_move ON public.stock_move_lines USING btree (move_id);


--
-- Name: idx_stock_moves_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_moves_dealer ON public.stock_moves USING btree (dealer_id);


--
-- Name: idx_strategies_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_strategies_tenant ON public.strategies USING btree (tenant_id);


--
-- Name: idx_temp_auth_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_temp_auth_dealer ON public.temp_authorizations USING btree (dealer_id, status);


--
-- Name: idx_user_roles_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_roles_role ON public.user_roles USING btree (role_id);


--
-- Name: idx_users_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_dealer ON public.users USING btree (dealer_id);


--
-- Name: idx_users_org; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_org ON public.users USING btree (org_id);


--
-- Name: idx_users_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_tenant ON public.users USING btree (tenant_id);


--
-- Name: idx_warehouses_dealer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warehouses_dealer ON public.warehouses USING btree (dealer_id);


--
-- Name: idx_wf_nodes_wf; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_nodes_wf ON public.workflow_nodes USING btree (workflow_id, seq);


--
-- Name: inventory_transactions_2026_07_product_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_transactions_2026_07_product_id_at_time_idx ON public.inventory_transactions_2026_07 USING btree (product_id, at_time DESC);


--
-- Name: inventory_transactions_2026_07_tenant_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_transactions_2026_07_tenant_id_at_time_idx ON public.inventory_transactions_2026_07 USING btree (tenant_id, at_time DESC);


--
-- Name: inventory_transactions_2026_08_product_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_transactions_2026_08_product_id_at_time_idx ON public.inventory_transactions_2026_08 USING btree (product_id, at_time DESC);


--
-- Name: inventory_transactions_2026_08_tenant_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_transactions_2026_08_tenant_id_at_time_idx ON public.inventory_transactions_2026_08 USING btree (tenant_id, at_time DESC);


--
-- Name: inventory_transactions_2026_09_product_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_transactions_2026_09_product_id_at_time_idx ON public.inventory_transactions_2026_09 USING btree (product_id, at_time DESC);


--
-- Name: inventory_transactions_2026_09_tenant_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_transactions_2026_09_tenant_id_at_time_idx ON public.inventory_transactions_2026_09 USING btree (tenant_id, at_time DESC);


--
-- Name: inventory_transactions_2026_10_product_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_transactions_2026_10_product_id_at_time_idx ON public.inventory_transactions_2026_10 USING btree (product_id, at_time DESC);


--
-- Name: inventory_transactions_2026_10_tenant_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_transactions_2026_10_tenant_id_at_time_idx ON public.inventory_transactions_2026_10 USING btree (tenant_id, at_time DESC);


--
-- Name: inventory_transactions_2026_11_product_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_transactions_2026_11_product_id_at_time_idx ON public.inventory_transactions_2026_11 USING btree (product_id, at_time DESC);


--
-- Name: inventory_transactions_2026_11_tenant_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_transactions_2026_11_tenant_id_at_time_idx ON public.inventory_transactions_2026_11 USING btree (tenant_id, at_time DESC);


--
-- Name: inventory_transactions_2026_12_product_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_transactions_2026_12_product_id_at_time_idx ON public.inventory_transactions_2026_12 USING btree (product_id, at_time DESC);


--
-- Name: inventory_transactions_2026_12_tenant_id_at_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_transactions_2026_12_tenant_id_at_time_idx ON public.inventory_transactions_2026_12 USING btree (tenant_id, at_time DESC);


--
-- Name: ux_main_wh; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_main_wh ON public.warehouses USING btree (tenant_id, dealer_id) WHERE (((type)::text = 'main'::text) AND ((status)::text = 'active'::text));


--
-- Name: ux_rcpt_serial; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_rcpt_serial ON public.receipt_lines USING btree (receipt_id, serial_no) WHERE (serial_no IS NOT NULL);


--
-- Name: ux_sales_serial; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_sales_serial ON public.sales_out_lines USING btree (serial_no) WHERE (serial_no IS NOT NULL);


--
-- Name: ux_users_wechat_openid; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_users_wechat_openid ON public.users USING btree (wechat_openid) WHERE (wechat_openid IS NOT NULL);


--
-- Name: audit_logs_2026_07_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.audit_logs_pkey ATTACH PARTITION public.audit_logs_2026_07_pkey;


--
-- Name: audit_logs_2026_07_resource_type_resource_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_resource ATTACH PARTITION public.audit_logs_2026_07_resource_type_resource_id_idx;


--
-- Name: audit_logs_2026_07_tenant_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_tenant_time ATTACH PARTITION public.audit_logs_2026_07_tenant_id_at_time_idx;


--
-- Name: audit_logs_2026_07_user_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_user_time ATTACH PARTITION public.audit_logs_2026_07_user_id_at_time_idx;


--
-- Name: audit_logs_2026_08_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.audit_logs_pkey ATTACH PARTITION public.audit_logs_2026_08_pkey;


--
-- Name: audit_logs_2026_08_resource_type_resource_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_resource ATTACH PARTITION public.audit_logs_2026_08_resource_type_resource_id_idx;


--
-- Name: audit_logs_2026_08_tenant_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_tenant_time ATTACH PARTITION public.audit_logs_2026_08_tenant_id_at_time_idx;


--
-- Name: audit_logs_2026_08_user_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_user_time ATTACH PARTITION public.audit_logs_2026_08_user_id_at_time_idx;


--
-- Name: audit_logs_2026_09_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.audit_logs_pkey ATTACH PARTITION public.audit_logs_2026_09_pkey;


--
-- Name: audit_logs_2026_09_resource_type_resource_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_resource ATTACH PARTITION public.audit_logs_2026_09_resource_type_resource_id_idx;


--
-- Name: audit_logs_2026_09_tenant_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_tenant_time ATTACH PARTITION public.audit_logs_2026_09_tenant_id_at_time_idx;


--
-- Name: audit_logs_2026_09_user_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_user_time ATTACH PARTITION public.audit_logs_2026_09_user_id_at_time_idx;


--
-- Name: audit_logs_2026_10_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.audit_logs_pkey ATTACH PARTITION public.audit_logs_2026_10_pkey;


--
-- Name: audit_logs_2026_10_resource_type_resource_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_resource ATTACH PARTITION public.audit_logs_2026_10_resource_type_resource_id_idx;


--
-- Name: audit_logs_2026_10_tenant_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_tenant_time ATTACH PARTITION public.audit_logs_2026_10_tenant_id_at_time_idx;


--
-- Name: audit_logs_2026_10_user_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_user_time ATTACH PARTITION public.audit_logs_2026_10_user_id_at_time_idx;


--
-- Name: audit_logs_2026_11_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.audit_logs_pkey ATTACH PARTITION public.audit_logs_2026_11_pkey;


--
-- Name: audit_logs_2026_11_resource_type_resource_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_resource ATTACH PARTITION public.audit_logs_2026_11_resource_type_resource_id_idx;


--
-- Name: audit_logs_2026_11_tenant_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_tenant_time ATTACH PARTITION public.audit_logs_2026_11_tenant_id_at_time_idx;


--
-- Name: audit_logs_2026_11_user_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_user_time ATTACH PARTITION public.audit_logs_2026_11_user_id_at_time_idx;


--
-- Name: audit_logs_2026_12_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.audit_logs_pkey ATTACH PARTITION public.audit_logs_2026_12_pkey;


--
-- Name: audit_logs_2026_12_resource_type_resource_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_resource ATTACH PARTITION public.audit_logs_2026_12_resource_type_resource_id_idx;


--
-- Name: audit_logs_2026_12_tenant_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_tenant_time ATTACH PARTITION public.audit_logs_2026_12_tenant_id_at_time_idx;


--
-- Name: audit_logs_2026_12_user_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_audit_user_time ATTACH PARTITION public.audit_logs_2026_12_user_id_at_time_idx;


--
-- Name: inventory_transactions_2026_07_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.inventory_transactions_pkey ATTACH PARTITION public.inventory_transactions_2026_07_pkey;


--
-- Name: inventory_transactions_2026_07_product_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_inv_txn_product ATTACH PARTITION public.inventory_transactions_2026_07_product_id_at_time_idx;


--
-- Name: inventory_transactions_2026_07_tenant_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_inv_txn_tenant_time ATTACH PARTITION public.inventory_transactions_2026_07_tenant_id_at_time_idx;


--
-- Name: inventory_transactions_2026_08_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.inventory_transactions_pkey ATTACH PARTITION public.inventory_transactions_2026_08_pkey;


--
-- Name: inventory_transactions_2026_08_product_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_inv_txn_product ATTACH PARTITION public.inventory_transactions_2026_08_product_id_at_time_idx;


--
-- Name: inventory_transactions_2026_08_tenant_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_inv_txn_tenant_time ATTACH PARTITION public.inventory_transactions_2026_08_tenant_id_at_time_idx;


--
-- Name: inventory_transactions_2026_09_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.inventory_transactions_pkey ATTACH PARTITION public.inventory_transactions_2026_09_pkey;


--
-- Name: inventory_transactions_2026_09_product_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_inv_txn_product ATTACH PARTITION public.inventory_transactions_2026_09_product_id_at_time_idx;


--
-- Name: inventory_transactions_2026_09_tenant_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_inv_txn_tenant_time ATTACH PARTITION public.inventory_transactions_2026_09_tenant_id_at_time_idx;


--
-- Name: inventory_transactions_2026_10_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.inventory_transactions_pkey ATTACH PARTITION public.inventory_transactions_2026_10_pkey;


--
-- Name: inventory_transactions_2026_10_product_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_inv_txn_product ATTACH PARTITION public.inventory_transactions_2026_10_product_id_at_time_idx;


--
-- Name: inventory_transactions_2026_10_tenant_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_inv_txn_tenant_time ATTACH PARTITION public.inventory_transactions_2026_10_tenant_id_at_time_idx;


--
-- Name: inventory_transactions_2026_11_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.inventory_transactions_pkey ATTACH PARTITION public.inventory_transactions_2026_11_pkey;


--
-- Name: inventory_transactions_2026_11_product_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_inv_txn_product ATTACH PARTITION public.inventory_transactions_2026_11_product_id_at_time_idx;


--
-- Name: inventory_transactions_2026_11_tenant_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_inv_txn_tenant_time ATTACH PARTITION public.inventory_transactions_2026_11_tenant_id_at_time_idx;


--
-- Name: inventory_transactions_2026_12_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.inventory_transactions_pkey ATTACH PARTITION public.inventory_transactions_2026_12_pkey;


--
-- Name: inventory_transactions_2026_12_product_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_inv_txn_product ATTACH PARTITION public.inventory_transactions_2026_12_product_id_at_time_idx;


--
-- Name: inventory_transactions_2026_12_tenant_id_at_time_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_inv_txn_tenant_time ATTACH PARTITION public.inventory_transactions_2026_12_tenant_id_at_time_idx;


--
-- Name: adjustment_lines adjustment_lines_adjustment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustment_lines
    ADD CONSTRAINT adjustment_lines_adjustment_id_fkey FOREIGN KEY (adjustment_id) REFERENCES public.inventory_adjustments(id) ON DELETE CASCADE;


--
-- Name: approval_history approval_history_task_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.approval_history
    ADD CONSTRAINT approval_history_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.approval_tasks(id) ON DELETE CASCADE;


--
-- Name: authorizations authorizations_contract_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.authorizations
    ADD CONSTRAINT authorizations_contract_id_fkey FOREIGN KEY (contract_id) REFERENCES public.contracts(id);


--
-- Name: authorizations authorizations_dealer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.authorizations
    ADD CONSTRAINT authorizations_dealer_id_fkey FOREIGN KEY (dealer_id) REFERENCES public.dealers(id);


--
-- Name: contract_applications contract_applications_dealer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_applications
    ADD CONSTRAINT contract_applications_dealer_id_fkey FOREIGN KEY (dealer_id) REFERENCES public.dealers(id);


--
-- Name: contract_diff contract_diff_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_diff
    ADD CONSTRAINT contract_diff_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.contract_applications(id) ON DELETE CASCADE;


--
-- Name: contract_signatures contract_signatures_contract_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_signatures
    ADD CONSTRAINT contract_signatures_contract_id_fkey FOREIGN KEY (contract_id) REFERENCES public.contracts(id) ON DELETE CASCADE;


--
-- Name: contracts contracts_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.contract_applications(id);


--
-- Name: contracts contracts_dealer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_dealer_id_fkey FOREIGN KEY (dealer_id) REFERENCES public.dealers(id);


--
-- Name: dealer_addresses dealer_addresses_dealer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dealer_addresses
    ADD CONSTRAINT dealer_addresses_dealer_id_fkey FOREIGN KEY (dealer_id) REFERENCES public.dealers(id);


--
-- Name: dealers dealers_parent_dealer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dealers
    ADD CONSTRAINT dealers_parent_dealer_id_fkey FOREIGN KEY (parent_dealer_id) REFERENCES public.dealers(id);


--
-- Name: dict_items dict_items_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dict_items
    ADD CONSTRAINT dict_items_type_id_fkey FOREIGN KEY (type_id) REFERENCES public.dict_types(id) ON DELETE CASCADE;


--
-- Name: distribution_lines distribution_lines_shipment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.distribution_lines
    ADD CONSTRAINT distribution_lines_shipment_id_fkey FOREIGN KEY (shipment_id) REFERENCES public.distribution_shipments(id) ON DELETE CASCADE;


--
-- Name: hospitals hospitals_region_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospitals
    ADD CONSTRAINT hospitals_region_id_fkey FOREIGN KEY (region_id) REFERENCES public.regions(id);


--
-- Name: inventory inventory_dealer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_dealer_id_fkey FOREIGN KEY (dealer_id) REFERENCES public.dealers(id);


--
-- Name: inventory inventory_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: inventory inventory_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(id);


--
-- Name: loan_lines loan_lines_loan_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_lines
    ADD CONSTRAINT loan_lines_loan_id_fkey FOREIGN KEY (loan_id) REFERENCES public.loans(id) ON DELETE CASCADE;


--
-- Name: order_lines order_lines_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_lines
    ADD CONSTRAINT order_lines_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: order_promotion_hits order_promotion_hits_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_promotion_hits
    ADD CONSTRAINT order_promotion_hits_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: orders orders_dealer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_dealer_id_fkey FOREIGN KEY (dealer_id) REFERENCES public.dealers(id);


--
-- Name: orders orders_parent_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_parent_order_id_fkey FOREIGN KEY (parent_order_id) REFERENCES public.orders(id);


--
-- Name: orders orders_ship_address_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_ship_address_id_fkey FOREIGN KEY (ship_address_id) REFERENCES public.dealer_addresses(id);


--
-- Name: org_units org_units_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.org_units
    ADD CONSTRAINT org_units_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.org_units(id);


--
-- Name: product_categories product_categories_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_categories
    ADD CONSTRAINT product_categories_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.product_categories(id);


--
-- Name: products products_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.product_categories(id);


--
-- Name: promotion_rules promotion_rules_promotion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotion_rules
    ADD CONSTRAINT promotion_rules_promotion_id_fkey FOREIGN KEY (promotion_id) REFERENCES public.promotions(id) ON DELETE CASCADE;


--
-- Name: promotion_status_logs promotion_status_logs_promotion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotion_status_logs
    ADD CONSTRAINT promotion_status_logs_promotion_id_fkey FOREIGN KEY (promotion_id) REFERENCES public.promotions(id) ON DELETE CASCADE;


--
-- Name: purchase_invoices purchase_invoices_ref_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_invoices
    ADD CONSTRAINT purchase_invoices_ref_order_id_fkey FOREIGN KEY (ref_order_id) REFERENCES public.orders(id);


--
-- Name: purchase_order_lines purchase_order_lines_po_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_lines
    ADD CONSTRAINT purchase_order_lines_po_id_fkey FOREIGN KEY (po_id) REFERENCES public.purchase_orders(id) ON DELETE CASCADE;


--
-- Name: receipt_lines receipt_lines_receipt_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipt_lines
    ADD CONSTRAINT receipt_lines_receipt_id_fkey FOREIGN KEY (receipt_id) REFERENCES public.receipts(id) ON DELETE CASCADE;


--
-- Name: receipts receipts_dealer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipts
    ADD CONSTRAINT receipts_dealer_id_fkey FOREIGN KEY (dealer_id) REFERENCES public.dealers(id);


--
-- Name: receipts receipts_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipts
    ADD CONSTRAINT receipts_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(id);


--
-- Name: regions regions_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regions
    ADD CONSTRAINT regions_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.regions(id);


--
-- Name: resources resources_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resources
    ADD CONSTRAINT resources_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.resources(id);


--
-- Name: rma_orders rma_orders_ref_rma_auth_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rma_orders
    ADD CONSTRAINT rma_orders_ref_rma_auth_id_fkey FOREIGN KEY (ref_rma_auth_id) REFERENCES public.rma_authorizations(id);


--
-- Name: role_strategies role_strategies_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_strategies
    ADD CONSTRAINT role_strategies_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: role_strategies role_strategies_strategy_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_strategies
    ADD CONSTRAINT role_strategies_strategy_id_fkey FOREIGN KEY (strategy_id) REFERENCES public.strategies(id) ON DELETE CASCADE;


--
-- Name: sales_invoices sales_invoices_ref_sales_out_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_invoices
    ADD CONSTRAINT sales_invoices_ref_sales_out_id_fkey FOREIGN KEY (ref_sales_out_id) REFERENCES public.sales_outs(id);


--
-- Name: sales_out_lines sales_out_lines_sales_out_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_out_lines
    ADD CONSTRAINT sales_out_lines_sales_out_id_fkey FOREIGN KEY (sales_out_id) REFERENCES public.sales_outs(id) ON DELETE CASCADE;


--
-- Name: sales_outs sales_outs_terminal_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_outs
    ADD CONSTRAINT sales_outs_terminal_id_fkey FOREIGN KEY (terminal_id) REFERENCES public.hospitals(id);


--
-- Name: stock_move_lines stock_move_lines_move_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_move_lines
    ADD CONSTRAINT stock_move_lines_move_id_fkey FOREIGN KEY (move_id) REFERENCES public.stock_moves(id) ON DELETE CASCADE;


--
-- Name: stocktake_lines stocktake_lines_stocktake_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_lines
    ADD CONSTRAINT stocktake_lines_stocktake_id_fkey FOREIGN KEY (stocktake_id) REFERENCES public.stocktakes(id) ON DELETE CASCADE;


--
-- Name: strategy_resources strategy_resources_resource_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.strategy_resources
    ADD CONSTRAINT strategy_resources_resource_id_fkey FOREIGN KEY (resource_id) REFERENCES public.resources(id) ON DELETE CASCADE;


--
-- Name: strategy_resources strategy_resources_strategy_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.strategy_resources
    ADD CONSTRAINT strategy_resources_strategy_id_fkey FOREIGN KEY (strategy_id) REFERENCES public.strategies(id) ON DELETE CASCADE;


--
-- Name: tenant_modules tenant_modules_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_modules
    ADD CONSTRAINT tenant_modules_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: user_roles user_roles_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: warehouses warehouses_dealer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_dealer_id_fkey FOREIGN KEY (dealer_id) REFERENCES public.dealers(id);


--
-- Name: warehouses warehouses_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: workflow_nodes workflow_nodes_workflow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_nodes
    ADD CONSTRAINT workflow_nodes_workflow_id_fkey FOREIGN KEY (workflow_id) REFERENCES public.workflows(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict ImBwzg8WA7vxbjRjUdTTlHHNqdUev5I2j0IDYvMh9lTgT2fPhXcQ6tw1lltyhyF

