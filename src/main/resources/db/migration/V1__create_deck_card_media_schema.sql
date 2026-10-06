create extension if not exists pgcrypto;

create table decks (
    id uuid primary key default gen_random_uuid(),
    public_id uuid not null default gen_random_uuid(),
    title varchar(200) not null,
    description varchar(2000),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_decks_public_id unique (public_id),
    constraint ck_decks_title_not_blank check (length(btrim(title)) > 0)
);

create table cards (
    id uuid primary key default gen_random_uuid(),
    front_content text not null default '',
    back_content text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table deck_cards (
    deck_id uuid not null,
    card_id uuid not null,
    created_at timestamptz not null default now(),
    primary key (deck_id, card_id),
    constraint fk_deck_cards_deck foreign key (deck_id) references decks (id) on delete cascade,
    constraint fk_deck_cards_card foreign key (card_id) references cards (id) on delete cascade
);

create table media (
    id uuid primary key default gen_random_uuid(),
    card_id uuid not null,
    side varchar(10) not null,
    storage_key varchar(500) not null,
    original_filename varchar(255),
    content_type varchar(100) not null,
    size_bytes bigint not null,
    width_px integer not null,
    height_px integer not null,
    alt_text varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_media_card foreign key (card_id) references cards (id) on delete cascade,
    constraint uk_media_card_side unique (card_id, side),
    constraint uk_media_storage_key unique (storage_key),
    constraint ck_media_side check (side in ('FRONT', 'BACK')),
    constraint ck_media_size_positive check (size_bytes > 0),
    constraint ck_media_width_positive check (width_px > 0),
    constraint ck_media_height_positive check (height_px > 0)
);

create index ix_deck_cards_card_id on deck_cards (card_id);
create index ix_media_card_id on media (card_id);
create index ix_decks_created_at_id on decks (created_at desc, id);
create index ix_cards_created_at_id on cards (created_at, id);
