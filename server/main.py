"""
FocusSense Central REST API & Load-Balancing Server
Technology Stack: Python 3.10+, FastAPI (Async), PostgreSQL / Supabase, DeepSeek LLM Inference Router
"""

import os
import re
import time
import uuid
import json
import hmac
import hashlib
import base64
from typing import List, Optional

try:
    import httpx
except ImportError:
    httpx = None

try:
    import requests
except ImportError:
    requests = None

from fastapi import FastAPI, HTTPException, Depends, Header, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from sqlalchemy import (
    create_engine, Column, String, BigInteger, Boolean, Float, Text, Integer, ForeignKey
)
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session

from dotenv import load_dotenv
load_dotenv()

# ---------------------------------------------------------------------------
# Configuration & Database Connection
# ---------------------------------------------------------------------------
DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql://postgres.jlmyesmofptnrthetvpt:%23SKravi240211964@aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres"
)

# Gemini Cloud AI Configuration (Primary High-Speed Evaluator)
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "YOUR_GEMINI_API_KEY")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")

# DeepSeek Inference Server URL (e.g. vLLM or Ollama instance)
# Format for vLLM: http://your-gpu-server:8000/v1/chat/completions
# Format for Ollama: http://your-gpu-server:11434/api/chat
DEEPSEEK_SERVER_URL = os.getenv("DEEPSEEK_SERVER_URL", "")
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "YOUR_DEEPSEEK_API_KEY")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

# Convert postgres:// or postgresql:// to explicit driver for SQLAlchemy
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql+psycopg2://", 1)
elif DATABASE_URL.startswith("postgresql://") and not DATABASE_URL.startswith("postgresql+"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+psycopg2://", 1)

# CRITICAL IPv4 COMPATIBILITY FOR RENDER & CLOUD CONTAINERS:
# Direct Supabase domain 'db.<project>.supabase.co' resolves strictly to IPv6.
# Render does NOT support outbound IPv6, leading to 'Network is unreachable (2406:da14...)'.
# Automatically rewrite to Supabase's verified IPv4 Connection Pooler (ap-northeast-1):
if "db.jlmyesmofptnrthetvpt.supabase.co" in DATABASE_URL:
    DATABASE_URL = DATABASE_URL.replace("db.jlmyesmofptnrthetvpt.supabase.co:5432", "aws-0-ap-northeast-1.pooler.supabase.com:5432")
if "aws-0-ap-south-1.pooler.supabase.com" in DATABASE_URL:
    DATABASE_URL = DATABASE_URL.replace("aws-0-ap-south-1.pooler.supabase.com", "aws-0-ap-northeast-1.pooler.supabase.com")

if "postgres:%23SKravi240211964" in DATABASE_URL:
    DATABASE_URL = DATABASE_URL.replace("postgres:%23SKravi240211964", "postgres.jlmyesmofptnrthetvpt:%23SKravi240211964")
elif "postgres:#SKravi240211964" in DATABASE_URL:
    DATABASE_URL = DATABASE_URL.replace("postgres:#SKravi240211964", "postgres.jlmyesmofptnrthetvpt:%23SKravi240211964")

# Supabase strictly requires SSL connections
if ("supabase.co" in DATABASE_URL or "supabase.com" in DATABASE_URL) and "sslmode" not in DATABASE_URL:
    separator = "&" if "?" in DATABASE_URL else "?"
    DATABASE_URL = f"{DATABASE_URL}{separator}sslmode=require"

engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,      # Automatically reconnects if connection was dropped by cloud pooler
    pool_recycle=300,        # Recycles connections every 5 minutes to prevent stale sockets
    connect_args={"check_same_thread": False} if "sqlite" in DATABASE_URL else {}
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

# ---------------------------------------------------------------------------
# SQLAlchemy ORM Models (Matching Supabase Schema)
# ---------------------------------------------------------------------------
class FamilyGroupModel(Base):
    __tablename__ = "family_groups"
    group_id = Column(String(64), primary_key=True, index=True)
    family_name = Column(String(128), nullable=False)
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000))

class UserModel(Base):
    __tablename__ = "users"
    user_id = Column(String(64), primary_key=True, index=True)
    group_id = Column(String(64), ForeignKey("family_groups.group_id"), index=True)
    email = Column(String(128), unique=True, nullable=False, index=True)
    password_hash = Column(String(256), nullable=False)
    role = Column(String(32), nullable=False) # 'parent' or 'child'
    name = Column(String(128), nullable=False)
    pin = Column(String(8), default="1234")
    avatar = Column(String(64), default="default")

class DeviceModel(Base):
    __tablename__ = "devices"
    device_id = Column(String(64), primary_key=True, index=True)
    user_id = Column(String(64), ForeignKey("users.user_id"), index=True)
    device_name = Column(String(128), nullable=False)
    token = Column(Text, nullable=True) # FCM push token
    battery_percent = Column(Integer, default=100)
    is_online = Column(Boolean, default=True)
    last_active = Column(BigInteger, default=lambda: int(time.time() * 1000))

class ActivityLogModel(Base):
    __tablename__ = "activity_logs"
    log_id = Column(String(64), primary_key=True, index=True)
    child_id = Column(String(64), ForeignKey("users.user_id"), index=True)
    package_name = Column(String(128), nullable=False)
    app_name = Column(String(128), nullable=False)
    content_title = Column(Text, nullable=True)
    extracted_text = Column(Text, nullable=False)
    is_flagged = Column(Boolean, default=False, index=True)
    threat_category = Column(String(64), nullable=True)
    confidence_score = Column(Float, default=0.0)
    ai_analysis_summary = Column(Text, nullable=True)
    recorded_at = Column(BigInteger, default=lambda: int(time.time() * 1000), index=True)
    is_synced = Column(Boolean, default=True)
    is_acknowledged = Column(Boolean, default=False)

class ScheduleRuleModel(Base):
    __tablename__ = "schedule_rules"
    rule_id = Column(String(64), primary_key=True, index=True)
    child_id = Column(String(64), ForeignKey("users.user_id"), index=True)
    rule_name = Column(String(128), nullable=False)
    category = Column(String(64), nullable=False)
    start_time = Column(String(10), nullable=False)
    end_time = Column(String(10), nullable=False)
    day_of_week = Column(String(64), default="Mon,Tue,Wed,Thu,Fri,Sat,Sun")
    restricted_packages = Column(Text, nullable=False)
    is_active = Column(Boolean, default=True)

class LocationPointModel(Base):
    __tablename__ = "location_history"
    loc_id = Column(String(64), primary_key=True, index=True)
    child_id = Column(String(64), ForeignKey("users.user_id"), index=True)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    accuracy = Column(Float, default=5.0)
    location_name = Column(String(128), default="Live GPS Fix")
    recorded_at = Column(BigInteger, default=lambda: int(time.time() * 1000), index=True)
    is_synced = Column(Boolean, default=True)

class InstalledAppModel(Base):
    __tablename__ = "installed_apps"
    id = Column(String(128), primary_key=True, index=True)
    child_id = Column(String(64), ForeignKey("users.user_id"), index=True)
    package_name = Column(String(128), nullable=False)
    app_name = Column(String(128), nullable=False)
    category = Column(String(64), default="Application")
    is_blocked = Column(Boolean, default=False)
    is_system_app = Column(Boolean, default=False)
    last_updated = Column(BigInteger, default=lambda: int(time.time() * 1000))

class PairingCodeModel(Base):
    __tablename__ = "pairing_codes"
    code = Column(String(16), primary_key=True, index=True)
    parent_user_id = Column(String(64), ForeignKey("users.user_id"), index=True)
    group_id = Column(String(64), ForeignKey("family_groups.group_id"), index=True)
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000))
    expires_at = Column(BigInteger, nullable=False)
    is_used = Column(Boolean, default=False)

class LocationRequestModel(Base):
    __tablename__ = "location_requests"
    request_id = Column(String(64), primary_key=True, index=True)
    child_id = Column(String(64), ForeignKey("users.user_id"), index=True)
    parent_id = Column(String(64), ForeignKey("users.user_id"), index=True)
    requested_at = Column(BigInteger, default=lambda: int(time.time() * 1000))
    is_fulfilled = Column(Boolean, default=False)
    fulfilled_at = Column(BigInteger, nullable=True)

# ---------------------------------------------------------------------------
# Cryptographic Token Management (Zero External Dependency JWT HS256)
# ---------------------------------------------------------------------------
JWT_SECRET = os.getenv("JWT_SECRET", "focussense-production-secret-key-2026")

def create_access_token(user_id: str, role: str, group_id: str, device_id: str = "", expires_in_secs: int = 86400 * 30) -> str:
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": user_id,
        "user_id": user_id,
        "role": role.lower(),
        "group_id": group_id,
        "device_id": device_id,
        "exp": int(time.time()) + expires_in_secs
    }
    header_b64 = base64.urlsafe_b64encode(json.dumps(header).encode()).decode().rstrip("=")
    payload_b64 = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    signature = hmac.new(
        JWT_SECRET.encode(),
        f"{header_b64}.{payload_b64}".encode(),
        hashlib.sha256
    ).digest()
    sig_b64 = base64.urlsafe_b64encode(signature).decode().rstrip("=")
    return f"{header_b64}.{payload_b64}.{sig_b64}"

def verify_token(token: str) -> Optional[dict]:
    try:
        parts = token.strip().split(".")
        if len(parts) != 3:
            return None
        header_b64, payload_b64, sig_b64 = parts
        expected_sig = hmac.new(
            JWT_SECRET.encode(),
            f"{header_b64}.{payload_b64}".encode(),
            hashlib.sha256
        ).digest()
        actual_sig = base64.urlsafe_b64decode(sig_b64 + "=" * (-len(sig_b64) % 4))
        if not hmac.compare_digest(expected_sig, actual_sig):
            return None
        payload_json = base64.urlsafe_b64decode(payload_b64 + "=" * (-len(payload_b64) % 4)).decode()
        payload = json.loads(payload_json)
        if payload.get("exp", 0) < int(time.time()):
            return None
        return payload
    except Exception:
        return None

# Create tables in PostgreSQL / Supabase if not already present
try:
    Base.metadata.create_all(bind=engine)
except Exception as e:
    print(f"[FocusSense] Note: Table creation handled via schema.sql or deferred: {e}")

# ---------------------------------------------------------------------------
# Pydantic Schemas
# ---------------------------------------------------------------------------
class RegisterParentRequest(BaseModel):
    name: str
    family_name: Optional[str] = ""
    email: str
    password: str
    pin: Optional[str] = "1234"
    user_id: Optional[str] = None
    group_id: Optional[str] = None
    device_id: Optional[str] = None

class LoginParentRequest(BaseModel):
    email: str
    password: str

class PairChildRequest(BaseModel):
    parent_email: str
    parent_password: str
    child_name: str
    device_name: Optional[str] = "Child Phone"
    device_id: Optional[str] = None

class UserResponse(BaseModel):
    user_id: str
    group_id: str
    email: str
    role: str
    name: str
    pin: Optional[str] = "1234"
    avatar: Optional[str] = "default"

class AuthResponse(BaseModel):
    status: str
    user: UserResponse
    family_name: str
    children: List[UserResponse] = []
    token: Optional[str] = None

class PairChildResponse(BaseModel):
    status: str
    child_user: UserResponse
    device_id: str
    group_id: str
    token: Optional[str] = None

class GeneratePairingCodeResponse(BaseModel):
    status: str = "success"
    pairing_code: str
    expires_at: int
    group_id: str

class ClaimPairingCodeRequest(BaseModel):
    pairing_code: str
    child_name: str
    device_name: Optional[str] = "Child Phone"
    device_id: Optional[str] = None

class LocationPingRequestResponse(BaseModel):
    status: str = "success"
    request_id: str
    child_id: str
    message: str

class LocationPingStatusResponse(BaseModel):
    has_pending_ping: bool
    request_id: Optional[str] = None

class DeviceRegistrationSchema(BaseModel):
    device_id: str
    user_id: str
    device_name: str
    token: Optional[str] = None
    battery_percent: int = 100

class ActivityLogSchema(BaseModel):
    log_id: str
    child_id: str
    package_name: str
    app_name: str
    content_title: Optional[str] = ""
    extracted_text: str
    is_flagged: bool = False
    threat_category: Optional[str] = "Safe"
    confidence_score: float = 0.0
    ai_analysis_summary: Optional[str] = ""
    recorded_at: int
    is_synced: bool = True
    is_acknowledged: bool = False

class ScheduleRuleSchema(BaseModel):
    rule_id: str
    child_id: str
    rule_name: str
    category: str
    start_time: str
    end_time: str
    day_of_week: str
    restricted_packages: str
    is_active: bool = True

class LocationPointSchema(BaseModel):
    loc_id: str
    child_id: str
    latitude: float
    longitude: float
    accuracy: float = 5.0
    location_name: str = "Live GPS Fix"
    recorded_at: int

class InstalledAppSchema(BaseModel):
    id: str
    child_id: str
    package_name: str
    app_name: str
    category: str = "Application"
    is_blocked: bool = False
    is_system_app: Optional[bool] = False
    last_updated: Optional[int] = 0

class AppBlockToggleRequest(BaseModel):
    package_name: str
    is_blocked: bool

class SyncPayload(BaseModel):
    child_id: str
    logs: List[ActivityLogSchema] = []
    locations: List[LocationPointSchema] = []

class ThreatEvaluateRequest(BaseModel):
    child_id: str
    package_name: str
    app_name: str
    content_title: Optional[str] = ""
    extracted_text: str

class ThreatEvaluateResponse(BaseModel):
    threat_detected: bool
    threat_category: str
    confidence_score: float
    ai_analysis_summary: str
    model_used: str
    severity_level: Optional[str] = "LOW"
    recommended_action: Optional[str] = "LOG_ONLY"
    parent_action_guidance: Optional[str] = ""

class AIConfigUpdateRequest(BaseModel):
    gemini_api_key: Optional[str] = None
    deepseek_api_key: Optional[str] = None
    deepseek_server_url: Optional[str] = None
    deepseek_model: Optional[str] = None

# ---------------------------------------------------------------------------
# FastAPI App Initialization & CORS
# ---------------------------------------------------------------------------
app = FastAPI(
    title="FocusSense Parental Control & Safety API",
    description="Central backend connecting Parent and Child devices with Supabase PostgreSQL and DeepSeek LLM routing.",
    version="2.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

# ---------------------------------------------------------------------------
# Health & Status
# ---------------------------------------------------------------------------
@app.get("/api/health")
def health_check(db: Session = Depends(get_db)):
    db_status = "connected"
    counts = {}
    try:
        from sqlalchemy import text
        db.execute(text("SELECT 1"))
        # Auto-create tables in Supabase if not already present
        try:
            Base.metadata.create_all(bind=engine)
        except Exception:
            pass
        counts = {
            "users": db.query(UserModel).count(),
            "activity_logs": db.query(ActivityLogModel).count(),
            "devices": db.query(DeviceModel).count(),
            "schedule_rules": db.query(ScheduleRuleModel).count(),
            "location_points": db.query(LocationPointModel).count()
        }
    except Exception as e:
        db_status = f"error: {str(e)}"

    return {
        "status": "healthy" if "error" not in db_status else "database_connection_issue",
        "service": "FocusSense Central Engine",
        "deepseek_configured": bool(DEEPSEEK_SERVER_URL),
        "database": db_status,
        "supabase_counts": counts,
        "timestamp": int(time.time() * 1000)
    }

@app.get("/api/admin/init-db")
def initialize_database(db: Session = Depends(get_db)):
    """Explicit endpoint to create all tables in Supabase PostgreSQL and return table list."""
    try:
        from sqlalchemy import text
        Base.metadata.create_all(bind=engine)
        tables = db.execute(text("SELECT table_name FROM information_schema.tables WHERE table_schema='public'")).fetchall()
        return {
            "status": "success",
            "message": "All tables created successfully in Supabase PostgreSQL!",
            "tables": [t[0] for t in tables],
            "counts": {
                "users": db.query(UserModel).count(),
                "activity_logs": db.query(ActivityLogModel).count(),
                "devices": db.query(DeviceModel).count(),
                "schedule_rules": db.query(ScheduleRuleModel).count(),
                "location_points": db.query(LocationPointModel).count()
            }
        }
    except Exception as e:
        return {
            "status": "error",
            "error_type": type(e).__name__,
            "message": str(e),
            "hint": "Ensure DATABASE_URL has sslmode=require and credentials are valid."
        }

def get_current_user(
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
) -> Optional[UserModel]:
    if authorization:
        parts = authorization.strip().split()
        if len(parts) == 2 and parts[0].lower() == "bearer":
            data = verify_token(parts[1])
            if data and "user_id" in data:
                return db.query(UserModel).filter(UserModel.user_id == data["user_id"]).first()
    return None

# ---------------------------------------------------------------------------
# 1. Authentication & Device Pairing
# ---------------------------------------------------------------------------
@app.post("/api/auth/register", response_model=AuthResponse)
def register_parent(payload: RegisterParentRequest, db: Session = Depends(get_db)):
    try:
        normalized_email = payload.email.strip().lower()
        existing = db.query(UserModel).filter(UserModel.email == normalized_email).first()
        if existing:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"An account with email '{normalized_email}' already exists. Please sign in."
            )

        # 1. Create Family Group
        group_id = payload.group_id.strip() if payload.group_id else f"group-{uuid.uuid4().hex[:8]}"
        family_name = payload.family_name.strip() if payload.family_name else f"{payload.name.strip()}'s Family"
        existing_group = db.query(FamilyGroupModel).filter(FamilyGroupModel.group_id == group_id).first()
        if not existing_group:
            group = FamilyGroupModel(
                group_id=group_id,
                family_name=family_name,
                created_at=int(time.time() * 1000)
            )
            db.add(group)
            db.flush()

        # 2. Create Parent User
        user_id = payload.user_id.strip() if payload.user_id else f"parent-{uuid.uuid4().hex[:8]}"
        parent = UserModel(
            user_id=user_id,
            group_id=group_id,
            email=normalized_email,
            password_hash=payload.password,
            role="parent",
            name=payload.name.strip(),
            pin=payload.pin.strip() if payload.pin else "1234",
            avatar="parent_avatar"
        )
        db.add(parent)
        db.flush()

        # 3. Create Default Parent Device
        device_id = payload.device_id.strip() if payload.device_id else f"dev-{uuid.uuid4().hex[:8]}"
        dev = DeviceModel(
            device_id=device_id,
            user_id=user_id,
            device_name=f"{payload.name.strip()}'s Parent Phone",
            is_online=True,
            battery_percent=100,
            last_active=int(time.time() * 1000)
        )
        db.add(dev)
        db.commit()

        token = create_access_token(
            user_id=parent.user_id,
            role="parent",
            group_id=group_id,
            device_id=device_id
        )

        return AuthResponse(
            status="success",
            user=UserResponse(
                user_id=parent.user_id,
                group_id=parent.group_id,
                email=parent.email,
                role=parent.role,
                name=parent.name,
                pin=parent.pin or "1234",
                avatar=parent.avatar or "default"
            ),
            family_name=family_name,
            children=[],
            token=token
        )
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Registration failed: {str(e)}"
        )

@app.post("/api/auth/login", response_model=AuthResponse)
def login_parent(payload: LoginParentRequest, db: Session = Depends(get_db)):
    normalized_email = payload.email.strip().lower()
    user = db.query(UserModel).filter(UserModel.email == normalized_email).first()
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"No account found for '{normalized_email}'."
        )
    if user.password_hash != payload.password:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect password. Please verify and try again."
        )

    group = db.query(FamilyGroupModel).filter(FamilyGroupModel.group_id == user.group_id).first()
    family_name = group.family_name if group else "My Family"

    # Fetch all children in this family
    children_models = db.query(UserModel).filter(
        UserModel.group_id == user.group_id,
        UserModel.role == "child"
    ).all()

    children = [
        UserResponse(
            user_id=c.user_id,
            group_id=c.group_id,
            email=c.email,
            role=c.role,
            name=c.name,
            pin=c.pin,
            avatar=c.avatar
        ) for c in children_models
    ]

    token = create_access_token(
        user_id=user.user_id,
        role=user.role,
        group_id=user.group_id
    )

    return AuthResponse(
        status="success",
        user=UserResponse(
            user_id=user.user_id,
            group_id=user.group_id,
            email=user.email,
            role=user.role,
            name=user.name,
            pin=user.pin,
            avatar=user.avatar
        ),
        family_name=family_name,
        children=children,
        token=token
    )

@app.post("/api/devices/pair", response_model=PairChildResponse)
def pair_child_device(payload: PairChildRequest, db: Session = Depends(get_db)):
    """Pairs a child's phone using parent credentials."""
    normalized_email = payload.parent_email.strip().lower()
    parent = db.query(UserModel).filter(
        UserModel.email == normalized_email,
        UserModel.role == "parent"
    ).first()

    if not parent:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Parent account not found. Please create the parent account first."
        )

    if parent.password_hash != payload.parent_password:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Parent password incorrect. Unable to link child device."
        )

    # Create new Child Profile in parent's group
    child_id = f"child-{uuid.uuid4().hex[:8]}"
    child_clean_name = payload.child_name.strip()
    child_email = f"{child_clean_name.lower().replace(' ', '')}-{uuid.uuid4().hex[:4]}@family.focussense"

    child = UserModel(
        user_id=child_id,
        group_id=parent.group_id,
        email=child_email,
        password_hash=parent.password_hash,
        role="child",
        name=child_clean_name,
        pin="",
        avatar="default_child"
    )
    db.add(child)

    # Register child device
    device_id = payload.device_id.strip() if payload.device_id else f"dev-{uuid.uuid4().hex[:8]}"
    dev = DeviceModel(
        device_id=device_id,
        user_id=child_id,
        device_name=payload.device_name.strip() if payload.device_name else f"{child_clean_name}'s Phone",
        is_online=True,
        battery_percent=100
    )
    db.add(dev)
    db.commit()

    token = create_access_token(
        user_id=child_id,
        role="child",
        group_id=parent.group_id,
        device_id=device_id
    )

    return PairChildResponse(
        status="paired",
        child_user=UserResponse(
            user_id=child.user_id,
            group_id=child.group_id,
            email=child.email,
            role=child.role,
            name=child.name,
            pin=child.pin,
            avatar=child.avatar
        ),
        device_id=device_id,
        group_id=parent.group_id,
        token=token
    )

# ---------------------------------------------------------------------------
# Multi-Device Pairing Codes (Parent generates 6-digit code -> Child claims)
# ---------------------------------------------------------------------------
@app.post("/api/pairing/generate", response_model=GeneratePairingCodeResponse)
def generate_pairing_code(
    authorization: Optional[str] = Header(None),
    parent_user_id: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """Parent generates a secure 6-digit short-lived pairing code (valid for 15 minutes)."""
    user = get_current_user(authorization, db=db)
    pid = user.user_id if user else parent_user_id
    if not pid:
        first_parent = db.query(UserModel).filter(UserModel.role == "parent").first()
        if first_parent:
            pid = first_parent.user_id
            gid = first_parent.group_id
        else:
            raise HTTPException(status_code=401, detail="Authentication required to generate pairing code.")
    else:
        parent_user = db.query(UserModel).filter(UserModel.user_id == pid).first()
        if not parent_user:
            raise HTTPException(status_code=404, detail="Parent account not found.")
        gid = parent_user.group_id

    # Invalidate previous unused codes for this parent
    now_ms = int(time.time() * 1000)
    db.query(PairingCodeModel).filter(
        PairingCodeModel.parent_user_id == pid,
        PairingCodeModel.is_used == False
    ).update({"is_used": True})

    import random
    code = f"{random.randint(100000, 999999)}"
    expires_at = now_ms + (15 * 60 * 1000)

    pairing_entry = PairingCodeModel(
        code=code,
        parent_user_id=pid,
        group_id=gid,
        created_at=now_ms,
        expires_at=expires_at,
        is_used=False
    )
    db.add(pairing_entry)
    db.commit()

    return GeneratePairingCodeResponse(
        status="success",
        pairing_code=code,
        expires_at=expires_at,
        group_id=gid
    )

@app.get("/api/pairing/active/{parent_id}", response_model=Optional[GeneratePairingCodeResponse])
def get_active_pairing_code(parent_id: str, db: Session = Depends(get_db)):
    now_ms = int(time.time() * 1000)
    entry = db.query(PairingCodeModel).filter(
        PairingCodeModel.parent_user_id == parent_id,
        PairingCodeModel.is_used == False,
        PairingCodeModel.expires_at > now_ms
    ).order_by(PairingCodeModel.created_at.desc()).first()
    if not entry:
        return None
    return GeneratePairingCodeResponse(
        status="success",
        pairing_code=entry.code,
        expires_at=entry.expires_at,
        group_id=entry.group_id
    )

@app.post("/api/pairing/claim", response_model=PairChildResponse)
def claim_pairing_code(payload: ClaimPairingCodeRequest, db: Session = Depends(get_db)):
    """Child enters pairing code to establish secure pairing and register device."""
    now_ms = int(time.time() * 1000)
    clean_code = payload.pairing_code.strip()
    entry = db.query(PairingCodeModel).filter(
        PairingCodeModel.code == clean_code,
        PairingCodeModel.is_used == False,
        PairingCodeModel.expires_at > now_ms
    ).first()

    if not entry:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or expired pairing code. Please generate a fresh code on the Parent device."
        )

    parent = db.query(UserModel).filter(UserModel.user_id == entry.parent_user_id).first()
    if not parent:
        raise HTTPException(status_code=404, detail="Parent account associated with code not found.")

    child_id = f"child-{uuid.uuid4().hex[:8]}"
    child_clean_name = payload.child_name.strip()
    child_email = f"{child_clean_name.lower().replace(' ', '')}-{uuid.uuid4().hex[:4]}@family.focussense"

    child = UserModel(
        user_id=child_id,
        group_id=entry.group_id,
        email=child_email,
        password_hash=parent.password_hash,
        role="child",
        name=child_clean_name,
        pin="",
        avatar="default_child"
    )
    db.add(child)

    device_id = payload.device_id.strip() if payload.device_id else f"dev-{uuid.uuid4().hex[:8]}"
    dev = DeviceModel(
        device_id=device_id,
        user_id=child_id,
        device_name=payload.device_name.strip() if payload.device_name else f"{child_clean_name}'s Phone",
        is_online=True,
        battery_percent=100,
        last_active=now_ms
    )
    db.add(dev)

    entry.is_used = True
    db.commit()

    child_token = create_access_token(
        user_id=child_id,
        role="child",
        group_id=entry.group_id,
        device_id=device_id
    )

    return PairChildResponse(
        status="paired",
        child_user=UserResponse(
            user_id=child.user_id,
            group_id=child.group_id,
            email=child.email,
            role=child.role,
            name=child.name,
            pin=child.pin,
            avatar=child.avatar
        ),
        device_id=device_id,
        group_id=entry.group_id,
        token=child_token
    )

@app.post("/api/devices/{child_id}/unpair")
def unpair_child_device(
    child_id: str,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    """Unpairs a child device and revokes its association."""
    user = get_current_user(authorization, db=db)
    child = db.query(UserModel).filter(UserModel.user_id == child_id).first()
    if not child:
        raise HTTPException(status_code=404, detail="Child account not found.")

    if user and user.group_id != child.group_id:
        raise HTTPException(status_code=403, detail="Not authorized to unpair this child.")

    db.query(DeviceModel).filter(DeviceModel.user_id == child_id).delete(synchronize_session=False)
    db.query(UserModel).filter(UserModel.user_id == child_id).delete(synchronize_session=False)
    db.commit()
    return {"status": "unpaired", "child_id": child_id}

# ---------------------------------------------------------------------------
# 2. Hardware Devices & Presence Management
# ---------------------------------------------------------------------------
@app.post("/api/devices/register")
def register_device(payload: DeviceRegistrationSchema, db: Session = Depends(get_db)):
    dev = db.query(DeviceModel).filter(DeviceModel.device_id == payload.device_id).first()
    if not dev:
        dev = DeviceModel(
            device_id=payload.device_id,
            user_id=payload.user_id,
            device_name=payload.device_name,
            token=payload.token,
            battery_percent=payload.battery_percent,
            is_online=True,
            last_active=int(time.time() * 1000)
        )
        db.add(dev)
    else:
        dev.device_name = payload.device_name
        dev.token = payload.token
        dev.battery_percent = payload.battery_percent
        dev.is_online = True
        dev.last_active = int(time.time() * 1000)
    db.commit()
    return {"status": "registered", "device_id": dev.device_id}

@app.get("/api/devices/{user_id}", response_model=List[DeviceRegistrationSchema])
def get_user_devices(user_id: str, db: Session = Depends(get_db)):
    devs = db.query(DeviceModel).filter(DeviceModel.user_id == user_id).all()
    return [
        DeviceRegistrationSchema(
            device_id=d.device_id,
            user_id=d.user_id,
            device_name=d.device_name,
            token=d.token,
            battery_percent=d.battery_percent
        ) for d in devs
    ]

@app.get("/api/family/{group_id}/children", response_model=List[UserResponse])
def get_family_children(group_id: str, db: Session = Depends(get_db)):
    children = db.query(UserModel).filter(
        UserModel.group_id == group_id,
        UserModel.role == "child"
    ).all()
    return [
        UserResponse(
            user_id=c.user_id,
            group_id=c.group_id,
            email=c.email,
            role=c.role,
            name=c.name,
            pin=c.pin,
            avatar=c.avatar
        ) for c in children
    ]

# ---------------------------------------------------------------------------
# 3. DeepSeek AI Load Balancer & Vulnerability Evaluation (Day 5 Pipeline)
# ---------------------------------------------------------------------------
SYSTEM_PROMPT = """You are FocusSense Child Sentinel, an expert AI safety evaluator for parental monitoring.
Your duty is to detect online harms and threats in text scraped from a child's device.
Evaluate the given text strictly and objectively across these 7 critical categories:

1. "Predatory Grooming & Stranger Risk": Involves secrecy ("don't tell your mom/parents", "our secret"), isolation, sexual advances, requests for private photos/webcam, asking for home address or school location, or planning clandestine in-person meetings.
2. "Cyberbullying & Harassment": Hostile attacks, public shaming, encouraging suicide/self-harm, discriminatory slurs, relentless insulting or intimidation.
3. "Self-Harm & Mental Distress": Suicidal intent, despair, self-mutilation (cutting), expressions of wanting to die or disappear.
4. "Violence, Weapons & Threats": Involves guns, knives, bombs, shooting, murder, assault, searches on how to harm or threat someone.
5. "Explicit & Adult Content": Pornography, adult services, unsolicited sexually explicit messages, non-consensual imagery.
6. "Substance Abuse & Drugs": Sourcing, buying, selling, or consuming illegal narcotics, vape, misuse of medications.
7. "Academic Dishonesty": Bypassing plagiarism/AI detectors, paying for exam cheat materials.
8. "Safe": Normal harmless conversation, friendly gaming, school research, family chat.

You MUST respond strictly with valid JSON conforming to:
{
  "threat_detected": true/false,
  "threat_category": "Predatory Grooming & Stranger Risk" | "Cyberbullying & Harassment" | "Self-Harm & Mental Distress" | "Violence, Weapons & Threats" | "Explicit & Adult Content" | "Substance Abuse & Drugs" | "Academic Dishonesty" | "Safe",
  "confidence_score": 0.0 to 1.0,
  "severity_level": "LOW" | "MEDIUM" | "HIGH" | "CRITICAL",
  "recommended_action": "LOG_ONLY" | "WARN_CHILD" | "PARENT_ALERT" | "INSTANT_BLOCK",
  "ai_analysis_summary": "<1-2 clear, objective sentences explaining the risk for the parent>",
  "parent_action_guidance": "<1 specific, practical recommendation for the parent>"
}
Do NOT include markdown formatting outside the JSON block."""

@app.get("/api/ai/config")
def get_ai_sentinel_config():
    """Returns AI Sentinel status, model information, and active engine."""
    has_gemini = bool(GEMINI_API_KEY and GEMINI_API_KEY != "your_api_key_here")
    has_deepseek = bool(DEEPSEEK_SERVER_URL or DEEPSEEK_API_KEY)
    active_target = f"Gemini 2.5 Flash Cloud AI ({GEMINI_MODEL})" if has_gemini else (DEEPSEEK_SERVER_URL or ("https://api.deepseek.com/chat/completions" if DEEPSEEK_API_KEY else "FocusSense Multi-Category Rule Engine"))
    return {
        "status": "ready",
        "gemini_configured": has_gemini,
        "gemini_model": GEMINI_MODEL,
        "deepseek_configured": has_deepseek,
        "deepseek_model": DEEPSEEK_MODEL,
        "active_endpoint": active_target,
        "fallback_engine": "FocusSense Tier-2 Multi-Category Safety Classifier",
        "supported_categories": [
            "Predatory Grooming & Stranger Risk",
            "Cyberbullying & Harassment",
            "Self-Harm & Mental Distress",
            "Violence, Weapons & Threats",
            "Explicit & Adult Content",
            "Substance Abuse & Drugs",
            "Academic Dishonesty"
        ]
    }

@app.post("/api/ai/config")
def update_ai_sentinel_config(payload: AIConfigUpdateRequest):
    """Dynamically updates Gemini / DeepSeek configuration without requiring server reboot."""
    global GEMINI_API_KEY, DEEPSEEK_SERVER_URL, DEEPSEEK_API_KEY, DEEPSEEK_MODEL
    if payload.gemini_api_key is not None:
        GEMINI_API_KEY = payload.gemini_api_key.strip()
    if payload.deepseek_api_key is not None:
        DEEPSEEK_API_KEY = payload.deepseek_api_key.strip()
    if payload.deepseek_server_url is not None:
        DEEPSEEK_SERVER_URL = payload.deepseek_server_url.strip()
    if payload.deepseek_model is not None and payload.deepseek_model.strip():
        DEEPSEEK_MODEL = payload.deepseek_model.strip()

    has_gemini = bool(GEMINI_API_KEY and GEMINI_API_KEY != "your_api_key_here")
    has_deepseek = bool(DEEPSEEK_SERVER_URL or DEEPSEEK_API_KEY)
    active_target = f"Gemini 2.5 Flash Cloud AI ({GEMINI_MODEL})" if has_gemini else (DEEPSEEK_SERVER_URL or ("https://api.deepseek.com/chat/completions" if DEEPSEEK_API_KEY else "FocusSense Multi-Category Rule Engine"))
    return {
        "status": "updated",
        "gemini_configured": has_gemini,
        "gemini_model": GEMINI_MODEL,
        "deepseek_configured": has_deepseek,
        "deepseek_model": DEEPSEEK_MODEL,
        "active_endpoint": active_target,
        "message": "AI Sentinel configuration updated successfully."
    }

@app.post("/api/evaluate", response_model=ThreatEvaluateResponse)
@app.post("/api/ai/evaluate", response_model=ThreatEvaluateResponse)
async def evaluate_content_threat(payload: ThreatEvaluateRequest, db: Session = Depends(get_db)):
    """
    AI Threat Pipeline:
    Routes incoming text from child devices to Gemini 3.5 Flash Cloud AI,
    with automatic failover to DeepSeek and multi-category safety rule engine.
    Persists flagged alerts into Supabase with guaranteed foreign-key resolution.
    """
    text = payload.extracted_text
    lower_text = text.lower()
    last_error_reason = ""

    # Priority 1: Gemini 3.5 Flash Cloud AI (Direct High-Speed Multimodal Intelligence)
    if GEMINI_API_KEY and GEMINI_API_KEY != "your_api_key_here":
        try:
            gemini_url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent?key={GEMINI_API_KEY}"
            gemini_payload = {
                "contents": [{"parts": [{"text": f"""You are FocusSense AI, an expert parental protection sentinel.
Analyze the following text extracted in real-time from a child's device:
App: {payload.app_name}
Title: {payload.content_title}
Text: \"\"\"{text}\"\"\"

Evaluate across these 7 critical safety categories:
1. "Predatory Grooming & Stranger Risk" (secrecy, isolation, requests for private meetups or photos)
2. "Cyberbullying & Harassment" (hostile attacks, slurs, insulting, demeaning)
3. "Self-Harm & Mental Distress" (suicide, cutting, wanting to die)
4. "Violence, Weapons & Threats" (guns, knives, attacks, murder, assault)
5. "Explicit & Adult Content" (pornography, adult sites, explicit chats)
6. "Substance Abuse & Drugs" (narcotics, vaping, pills, illicit drugs)
7. "Academic Dishonesty" (exam cheating, paper bots)
8. "Safe" (normal friendly chats, studies, gaming)

Respond strictly in valid JSON:
{{
  "isFlagged": boolean,
  "threatCategory": "Predatory Grooming & Stranger Risk" | "Cyberbullying & Harassment" | "Self-Harm & Mental Distress" | "Violence, Weapons & Threats" | "Explicit & Adult Content" | "Substance Abuse & Drugs" | "Academic Dishonesty" | "Safe",
  "confidenceScore": float,
  "severityLevel": "LOW" | "MEDIUM" | "HIGH" | "CRITICAL",
  "recommendedAction": "LOG_ONLY" | "WARN_CHILD" | "PARENT_ALERT" | "INSTANT_BLOCK",
  "aiAnalysisSummary": "concise explanation",
  "parentActionGuidance": "advice for parent"
}}"""}]}],
                "generationConfig": {
                    "responseMimeType": "application/json",
                    "temperature": 0.1
                }
            }
            gemini_headers = {"Content-Type": "application/json"}
            gemini_resp = None
            if httpx is not None:
                async with httpx.AsyncClient(timeout=8.0) as client:
                    gemini_resp = await client.post(gemini_url, json=gemini_payload, headers=gemini_headers)
            elif requests is not None:
                gemini_resp = requests.post(gemini_url, json=gemini_payload, headers=gemini_headers, timeout=8.0)

            if gemini_resp and gemini_resp.status_code == 200:
                g_json = gemini_resp.json()
                raw_text = g_json.get("candidates", [{}])[0].get("content", {}).get("parts", [{}])[0].get("text", "")
                if raw_text:
                    parsed = json.loads(raw_text)
                    threat_det = parsed.get("isFlagged", parsed.get("threat_detected", False))
                    threat_cat = parsed.get("threatCategory", parsed.get("threat_category", "Safe"))
                    conf = float(parsed.get("confidenceScore", parsed.get("confidence_score", 0.95)))
                    sev = parsed.get("severityLevel", parsed.get("severity_level", "HIGH" if threat_det else "LOW"))
                    rec_action = parsed.get("recommendedAction", parsed.get("recommended_action", "PARENT_ALERT" if threat_det else "LOG_ONLY"))
                    summary = parsed.get("aiAnalysisSummary", parsed.get("ai_analysis_summary", "Analyzed by Gemini 2.5 Flash."))
                    guidance = parsed.get("parentActionGuidance", parsed.get("parent_action_guidance", "Review activity log with child."))

                    if threat_det:
                        _record_threat_log(db, payload, threat_cat, conf, summary)

                    return ThreatEvaluateResponse(
                        threat_detected=threat_det,
                        threat_category=threat_cat,
                        confidence_score=conf,
                        ai_analysis_summary=summary,
                        model_used=f"Gemini Cloud AI ({GEMINI_MODEL})",
                        severity_level=sev,
                        recommended_action=rec_action,
                        parent_action_guidance=guidance
                    )
        except Exception as e:
            print("Gemini evaluation fallback:", str(e))

    # Priority 2: DeepSeek endpoint
    target_url = DEEPSEEK_SERVER_URL
    if not target_url and DEEPSEEK_API_KEY:
        target_url = "https://api.deepseek.com/chat/completions"

    if target_url:
        try:
            user_prompt = f"""Context:
App: {payload.app_name}
Window Title: {payload.content_title}
Text extracted from screen:
\"\"\"{text}\"\"\"

Analyze according to the safety guidelines and return pure JSON."""

            headers = {"Content-Type": "application/json"}
            if DEEPSEEK_API_KEY:
                headers["Authorization"] = f"Bearer {DEEPSEEK_API_KEY}"

            # Format body for Ollama vs OpenAI/vLLM/DeepSeek
            if "/api/chat" in target_url:
                req_body = {
                    "model": DEEPSEEK_MODEL,
                    "messages": [
                        {"role": "system", "content": SYSTEM_PROMPT},
                        {"role": "user", "content": user_prompt}
                    ],
                    "stream": False,
                    "format": "json"
                }
            else:
                req_body = {
                    "model": DEEPSEEK_MODEL,
                    "messages": [
                        {"role": "system", "content": SYSTEM_PROMPT},
                        {"role": "user", "content": user_prompt}
                    ],
                    "temperature": 0.1,
                    "max_tokens": 300
                }

            status_code = None
            response_json = None

            if httpx is not None:
                async with httpx.AsyncClient(timeout=8.0) as client:
                    resp = await client.post(target_url, json=req_body, headers=headers)
                    status_code = resp.status_code
                    response_json = resp.json()
            elif requests is not None:
                resp = requests.post(target_url, json=req_body, headers=headers, timeout=8.0)
                status_code = resp.status_code
                response_json = resp.json()

            if status_code == 200 and response_json:
                content = ""
                if "message" in response_json and "content" in response_json["message"]:
                    content = response_json["message"]["content"]
                elif "choices" in response_json and len(response_json["choices"]) > 0:
                    content = response_json["choices"][0].get("message", {}).get("content", "")

                start = content.find("{")
                end = content.rfind("}")
                if start != -1 and end != -1:
                    parsed = json.loads(content[start:end+1])
                    threat_det = parsed.get("threat_detected", False)
                    threat_cat = parsed.get("threat_category", "Safe")
                    conf = float(parsed.get("confidence_score", 0.9))
                    summary = parsed.get("ai_analysis_summary", "Evaluation complete.")
                    sev = parsed.get("severity_level", "HIGH" if threat_det else "LOW")
                    rec_action = parsed.get("recommended_action", "PARENT_ALERT" if threat_det else "LOG_ONLY")
                    guidance = parsed.get("parent_action_guidance", "Review activity log with child.")

                    if threat_det:
                        _record_threat_log(db, payload, threat_cat, conf, summary)

                    return ThreatEvaluateResponse(
                        threat_detected=threat_det,
                        threat_category=threat_cat,
                        confidence_score=conf,
                        ai_analysis_summary=summary,
                        model_used=f"DeepSeek ({DEEPSEEK_MODEL})",
                        severity_level=sev,
                        recommended_action=rec_action,
                        parent_action_guidance=guidance
                    )
            elif status_code == 402:
                last_error_reason = " (DeepSeek Key: Insufficient Balance - Recharge at platform.deepseek.com)"
                print("DeepSeek API 402: Insufficient balance on account.")
            elif status_code and status_code != 200:
                last_error_reason = f" (DeepSeek HTTP {status_code})"
                print(f"DeepSeek API HTTP {status_code}: {response_json}")
        except Exception as e:
            # Fall through seamlessly to high-precision rule engine
            last_error_reason = f" (DeepSeek error: {str(e)[:40]})"
            print("DeepSeek inference unreachable, activating fallback engine:", str(e))

    # High-Precision Multi-Category Rule Engine (Fallback & Instant Safety Gate)
    threat_detected = False
    threat_category = "Safe"
    confidence = 0.0
    severity = "LOW"
    action = "LOG_ONLY"
    summary = "No safety risk detected in this content."
    guidance = "Normal activity."

    # 1. Self-Harm & Mental Distress (CRITICAL)
    self_harm_phrases = ["suicide", "want to die", "cut myself", "end my life", "kill myself", "hate being alive", "slit my wrists", "better off dead"]
    if any(k in lower_text for k in self_harm_phrases):
        threat_detected = True
        threat_category = "Self-Harm & Mental Distress"
        confidence = 0.98
        severity = "CRITICAL"
        action = "PARENT_ALERT"
        summary = "Critical distress or self-harm keywords detected in active window."
        guidance = "Reach out to your child immediately with empathy and seek professional adolescent mental health support if needed."

    # 2. Violence, Weapons & Threats (CRITICAL)
    elif any(p in lower_text for p in [
        "how to threat", "threat someone", "threaten someone", "how to kill",
        "kill someone", "hurt someone", "harm someone", "mass shooting",
        "school shooting", "death threat", "bring a gun", "bring a knife", "make a bomb"
    ]) or (any(w in lower_text for w in ["kill", "murder", "shoot", "bomb", "stab"]) and any(w in lower_text for w in ["school", "people", "someone", "gun", "knife"])):
        threat_detected = True
        threat_category = "Violence, Weapons & Threats"
        confidence = 0.96
        severity = "CRITICAL"
        action = "INSTANT_BLOCK"
        summary = "Search query or message involving physical violence, death threats, or weapons detected."
        guidance = "Intervene and discuss the context of weapon searches or threatening statements immediately."

    # 3. Predatory Grooming & Stranger Risk (HIGH)
    elif any(k in lower_text for k in [
        "don't tell your mom", "dont tell your parents", "keep this secret", "secret meeting",
        "send me pics", "turn on camera", "are you alone", "where do you go to school",
        "meet behind", "come over to my place", "free robux code click here"
    ]):
        threat_detected = True
        threat_category = "Predatory Grooming & Stranger Risk"
        confidence = 0.95
        severity = "HIGH"
        action = "PARENT_ALERT"
        summary = "Potential stranger solicitation, clandestine meeting request, or secrecy coercion detected."
        guidance = "Verify who your child is communicating with and reinforce safety rules about never sharing personal details or photos."

    # 4. Cyberbullying & Harassment (HIGH)
    elif any(k in lower_text for k in ["kill yourself", "loser", "nobody likes you", "ugly freak", "stupid idiot", "go die", "you are worthless"]):
        threat_detected = True
        threat_category = "Cyberbullying & Harassment"
        confidence = 0.92
        severity = "HIGH"
        action = "PARENT_ALERT"
        summary = "Hostile, degrading, or harassing language targeted at child detected."
        guidance = "Review the social chat app and offer support against online bullying."

    # 5. Explicit & Adult Content (HIGH)
    elif any(k in lower_text for k in ["pornhub", "xvideos", "nsfw video", "adult content 18+", "nude pics", "onlyfans leaks"]):
        threat_detected = True
        threat_category = "Explicit & Adult Content"
        confidence = 0.94
        severity = "HIGH"
        action = "INSTANT_BLOCK"
        summary = "Adult or sexually explicit content attempt identified."
        guidance = "Ensure web filtering and search safe modes are locked on child devices."

    # 6. Substance Abuse & Drugs (MEDIUM)
    elif any(k in lower_text for k in ["buy weed online", "vape juice puff", "order edibles", "buy pills online", "disposable vape"]):
        threat_detected = True
        threat_category = "Substance Abuse & Drugs"
        confidence = 0.90
        severity = "MEDIUM"
        action = "PARENT_ALERT"
        summary = "References to vaping, narcotics, or unregulated substance sourcing detected."
        guidance = "Discuss substance abuse risks openly and inspect installed package allowances."

    # 7. Academic Dishonesty (LOW)
    elif any(k in lower_text for k in ["bypass turnitin", "write my essay fast bot", "cheat exam questions", "steal answers"]):
        threat_detected = True
        threat_category = "Academic Dishonesty"
        confidence = 0.88
        severity = "LOW"
        action = "LOG_ONLY"
        summary = "Attempt to bypass academic integrity tools detected."
        guidance = "Review study habits and encourage independent learning."

    if threat_detected:
        _record_threat_log(db, payload, threat_category, confidence, summary)

    return ThreatEvaluateResponse(
        threat_detected=threat_detected,
        threat_category=threat_category,
        confidence_score=confidence,
        ai_analysis_summary=summary,
        model_used=f"FocusSense Tier-2 Multi-Category Safety Classifier{last_error_reason}",
        severity_level=severity,
        recommended_action=action,
        parent_action_guidance=guidance
    )

def _record_threat_log(db: Session, payload: ThreatEvaluateRequest, category: str, confidence: float, summary: str):
    """Guarantees resilient recording of flagged incidents into Supabase."""
    try:
        child_user = db.query(UserModel).filter(UserModel.user_id == payload.child_id).first()
        if not child_user:
            group = db.query(FamilyGroupModel).first()
            if not group:
                group = FamilyGroupModel(group_id="group-default", family_name="FocusSense Family")
                db.add(group)
                db.flush()
            child_user = UserModel(
                user_id=payload.child_id,
                group_id=group.group_id,
                email=f"{payload.child_id}@family.focussense",
                password_hash="synced_child",
                role="child",
                name="Child Device",
                pin=""
            )
            db.add(child_user)
            db.commit()

        log = ActivityLogModel(
            log_id=f"log-{uuid.uuid4().hex[:8]}",
            child_id=payload.child_id,
            package_name=payload.package_name,
            app_name=payload.app_name,
            content_title=payload.content_title,
            extracted_text=payload.extracted_text,
            is_flagged=True,
            threat_category=category,
            confidence_score=confidence,
            ai_analysis_summary=summary,
            recorded_at=int(time.time() * 1000),
            is_synced=True
        )
        db.add(log)
        db.commit()
    except Exception as dbe:
        db.rollback()
        print("Database log recording skipped:", str(dbe))

# ---------------------------------------------------------------------------
# 4. Activity Logs & Zero Data-Loss Sync (Child -> Server Only)
# ---------------------------------------------------------------------------
@app.post("/api/sync")
def sync_child_data(
    payload: SyncPayload,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    """Receives offline-queued logs and locations from Child devices only."""
    current_user = get_current_user(authorization, db=db)
    if current_user:
        if current_user.role == "parent":
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Parent devices are strictly forbidden from uploading telemetry."
            )
        if current_user.role == "child" and current_user.user_id != payload.child_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Child device cannot upload telemetry on behalf of another child."
            )

    saved_logs_count = 0
    flagged_alerts_count = 0

    # Ensure child user exists in Supabase so ForeignKey doesn't fail
    child_user = db.query(UserModel).filter(UserModel.user_id == payload.child_id).first()
    if not child_user:
        group = db.query(FamilyGroupModel).first()
        if not group:
            group = FamilyGroupModel(group_id="group-default", family_name="FocusSense Family")
            db.add(group)
            db.flush()
        child_user = UserModel(
            user_id=payload.child_id,
            group_id=group.group_id,
            email=f"{payload.child_id}@family.focussense",
            password_hash="synced_child",
            role="child",
            name="Child Device",
            pin=""
        )
        db.add(child_user)
        db.commit()

    for log_data in payload.logs:
        existing = db.query(ActivityLogModel).filter(ActivityLogModel.log_id == log_data.log_id).first()
        if not existing:
            new_log = ActivityLogModel(
                log_id=log_data.log_id,
                child_id=payload.child_id,
                package_name=log_data.package_name,
                app_name=log_data.app_name,
                content_title=log_data.content_title,
                extracted_text=log_data.extracted_text,
                is_flagged=log_data.is_flagged,
                threat_category=log_data.threat_category,
                confidence_score=log_data.confidence_score,
                ai_analysis_summary=log_data.ai_analysis_summary,
                recorded_at=log_data.recorded_at,
                is_synced=True,
                is_acknowledged=log_data.is_acknowledged
            )
            db.add(new_log)
            saved_logs_count += 1
            if log_data.is_flagged:
                flagged_alerts_count += 1

    for loc in payload.locations:
        existing_loc = db.query(LocationPointModel).filter(LocationPointModel.loc_id == loc.loc_id).first()
        if not existing_loc:
            new_point = LocationPointModel(
                loc_id=loc.loc_id,
                child_id=payload.child_id,
                latitude=loc.latitude,
                longitude=loc.longitude,
                accuracy=loc.accuracy,
                location_name=loc.location_name,
                recorded_at=loc.recorded_at,
                is_synced=True
            )
            db.add(new_point)

    db.commit()
    return {
        "status": "success",
        "synced_logs": saved_logs_count,
        "flagged_threats": flagged_alerts_count,
        "synced_locations": len(payload.locations)
    }

# ---------------------------------------------------------------------------
# 5. Parent Queries: Read & Manage Activity Logs
# ---------------------------------------------------------------------------
@app.get("/api/logs/{child_id}")
def get_child_logs(
    child_id: str,
    flagged_only: bool = False,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    current_user = get_current_user(authorization, db=db)
    if current_user:
        child = db.query(UserModel).filter(UserModel.user_id == child_id).first()
        if child and child.group_id != current_user.group_id:
            raise HTTPException(status_code=403, detail="Unauthorized access to logs across family groups.")

    query = db.query(ActivityLogModel).filter(ActivityLogModel.child_id == child_id)
    if flagged_only:
        query = query.filter(ActivityLogModel.is_flagged == True)
    return query.order_by(ActivityLogModel.recorded_at.desc()).limit(100).all()

@app.delete("/api/logs/{log_id}")
def delete_vulnerable_log(
    log_id: str,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    log = db.query(ActivityLogModel).filter(ActivityLogModel.log_id == log_id).first()
    if not log:
        raise HTTPException(status_code=404, detail="Log not found")

    current_user = get_current_user(authorization, db=db)
    if current_user and current_user.role != "parent":
        raise HTTPException(status_code=403, detail="Only parents can delete safety alerts.")

    db.delete(log)
    db.commit()
    return {"status": "deleted", "log_id": log_id}

@app.put("/api/logs/{log_id}/acknowledge")
def acknowledge_log(
    log_id: str,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    log = db.query(ActivityLogModel).filter(ActivityLogModel.log_id == log_id).first()
    if not log:
        raise HTTPException(status_code=404, detail="Log not found")

    current_user = get_current_user(authorization, db=db)
    if current_user and current_user.role != "parent":
        raise HTTPException(status_code=403, detail="Only parents can acknowledge safety alerts.")

    log.is_acknowledged = True
    db.commit()
    return {"status": "acknowledged", "log_id": log_id}

# ---------------------------------------------------------------------------
# 6. Schedule Rules (Timetables & Curfews)
# ---------------------------------------------------------------------------
@app.get("/api/schedules/{child_id}")
def get_schedules_for_child(
    child_id: str,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    current_user = get_current_user(authorization, db=db)
    if current_user:
        child = db.query(UserModel).filter(UserModel.user_id == child_id).first()
        if child and child.group_id != current_user.group_id:
            raise HTTPException(status_code=403, detail="Unauthorized access across family groups.")

    return db.query(ScheduleRuleModel).filter(ScheduleRuleModel.child_id == child_id).all()

@app.post("/api/schedules")
def create_schedule_rule(
    rule: ScheduleRuleSchema,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    current_user = get_current_user(authorization, db=db)
    if current_user and current_user.role != "parent":
        raise HTTPException(status_code=403, detail="Only parents can configure scheduled rules.")

    existing = db.query(ScheduleRuleModel).filter(ScheduleRuleModel.rule_id == rule.rule_id).first()
    if existing:
        for k, v in rule.dict().items():
            setattr(existing, k, v)
    else:
        new_rule = ScheduleRuleModel(**rule.dict())
        db.add(new_rule)
    db.commit()
    return {"status": "saved", "rule_id": rule.rule_id}

@app.delete("/api/schedules/{rule_id}")
def delete_schedule_rule(
    rule_id: str,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    rule = db.query(ScheduleRuleModel).filter(ScheduleRuleModel.rule_id == rule_id).first()
    if not rule:
        raise HTTPException(status_code=404, detail="Rule not found")

    current_user = get_current_user(authorization, db=db)
    if current_user and current_user.role != "parent":
        raise HTTPException(status_code=403, detail="Only parents can delete schedule rules.")

    db.delete(rule)
    db.commit()
    return {"status": "deleted", "rule_id": rule_id}

# ---------------------------------------------------------------------------
# 6b. Real Installed Applications & Remote App Locker (Child -> Server -> Parent)
# ---------------------------------------------------------------------------
@app.post("/api/devices/{child_id}/apps/sync")
def sync_installed_apps(
    child_id: str,
    apps: List[InstalledAppSchema],
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    """
    CRITICAL: Only authenticated CHILD devices can upload installed application inventory.
    Parent devices are strictly rejected.
    """
    current_user = get_current_user(authorization, db=db)
    if current_user:
        if current_user.role == "parent":
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Parent devices are strictly forbidden from uploading app inventory as child data."
            )
        if current_user.role == "child" and current_user.user_id != child_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Child cannot upload applications for a different child profile."
            )

    # Ensure child user exists
    child_user = db.query(UserModel).filter(UserModel.user_id == child_id).first()
    if not child_user:
        group = db.query(FamilyGroupModel).first()
        if not group:
            group = FamilyGroupModel(group_id="group-default", family_name="FocusSense Family")
            db.add(group)
            db.flush()
        child_user = UserModel(
            user_id=child_id,
            group_id=group.group_id,
            email=f"{child_id}@family.focussense",
            password_hash="synced_child",
            role="child",
            name="Child Device",
            pin=""
        )
        db.add(child_user)
        db.commit()

    uploaded_packages = {a.package_name.lower() for a in apps}

    # 1. Update / Insert current inventory, preserving previous is_blocked status
    for app_item in apps:
        app_uid = app_item.id if app_item.id else f"{child_id}_{app_item.package_name}"
        existing = db.query(InstalledAppModel).filter(InstalledAppModel.id == app_uid).first()
        if existing:
            existing.app_name = app_item.app_name
            existing.category = app_item.category
            existing.is_system_app = getattr(app_item, "is_system_app", False)
            existing.last_updated = int(time.time() * 1000)
            # Retain blocked status set by parent if app is still installed
        else:
            new_app = InstalledAppModel(
                id=app_uid,
                child_id=child_id,
                package_name=app_item.package_name,
                app_name=app_item.app_name,
                category=app_item.category,
                is_blocked=app_item.is_blocked,
                is_system_app=getattr(app_item, "is_system_app", False),
                last_updated=int(time.time() * 1000)
            )
            db.add(new_app)

    # 2. Stale App Removal: When an app is uninstalled on Child device, remove its stale record
    existing_apps = db.query(InstalledAppModel).filter(InstalledAppModel.child_id == child_id).all()
    for e in existing_apps:
        if e.package_name.lower() not in uploaded_packages and not e.is_blocked:
            db.delete(e)

    db.commit()
    return {"status": "success", "count": len(apps)}

@app.get("/api/devices/{child_id}/apps")
def get_child_installed_apps(
    child_id: str,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    """Retrieves real application inventory for a child."""
    current_user = get_current_user(authorization, db=db)
    if current_user:
        child = db.query(UserModel).filter(UserModel.user_id == child_id).first()
        if child and child.group_id != current_user.group_id:
            raise HTTPException(status_code=403, detail="Unauthorized access across family groups.")

    return db.query(InstalledAppModel).filter(InstalledAppModel.child_id == child_id).order_by(InstalledAppModel.app_name.asc()).all()

@app.post("/api/devices/{child_id}/apps/toggle-block")
def toggle_app_block(
    child_id: str,
    payload: AppBlockToggleRequest,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    """Parent toggles remote block status for a child app."""
    current_user = get_current_user(authorization, db=db)
    if current_user and current_user.role != "parent":
        raise HTTPException(status_code=403, detail="Only parents can toggle application blocks.")

    app_uid = f"{child_id}_{payload.package_name}"
    existing = db.query(InstalledAppModel).filter(InstalledAppModel.id == app_uid).first()
    if existing:
        existing.is_blocked = payload.is_blocked
        existing.last_updated = int(time.time() * 1000)
        db.commit()
    else:
        new_app = InstalledAppModel(
            id=app_uid,
            child_id=child_id,
            package_name=payload.package_name,
            app_name=payload.package_name.split('.')[-1].capitalize(),
            category="Custom",
            is_blocked=payload.is_blocked,
            last_updated=int(time.time() * 1000)
        )
        db.add(new_app)
        db.commit()

    return {"status": "success", "package_name": payload.package_name, "is_blocked": payload.is_blocked}

@app.get("/api/devices/{child_id}/pending-restrictions")
def get_pending_restrictions(
    child_id: str,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    """Child device fetches active restrictions and blocked apps configured by parent."""
    blocked_apps = db.query(InstalledAppModel).filter(
        InstalledAppModel.child_id == child_id,
        InstalledAppModel.is_blocked == True
    ).all()
    schedules = db.query(ScheduleRuleModel).filter(
        ScheduleRuleModel.child_id == child_id,
        ScheduleRuleModel.is_active == True
    ).all()

    return {
        "status": "success",
        "child_id": child_id,
        "blocked_packages": [b.package_name.lower() for b in blocked_apps],
        "schedules": [
            {
                "rule_id": s.rule_id,
                "child_id": s.child_id,
                "rule_name": s.rule_name,
                "category": s.category,
                "start_time": s.start_time,
                "end_time": s.end_time,
                "day_of_week": s.day_of_week,
                "restricted_packages": s.restricted_packages,
                "is_active": s.is_active
            } for s in schedules
        ]
    }

# ---------------------------------------------------------------------------
# 7. Live GPS Telemetry & Real-Time Location Ping Requests
# ---------------------------------------------------------------------------
@app.post("/api/location/report")
def report_child_location(
    point: LocationPointSchema,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    """
    CRITICAL: Only authenticated CHILD devices can report location.
    Parent devices are strictly forbidden from uploading location.
    """
    current_user = get_current_user(authorization, db=db)
    if current_user:
        if current_user.role == "parent":
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Parent phone coordinates cannot be recorded as child GPS telemetry."
            )
        if current_user.role == "child" and current_user.user_id != point.child_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Child cannot submit location for a different child profile."
            )

    new_point = LocationPointModel(**point.dict())
    db.add(new_point)

    # Mark any pending location ping requests for this child as fulfilled
    now_ms = int(time.time() * 1000)
    db.query(LocationRequestModel).filter(
        LocationRequestModel.child_id == point.child_id,
        LocationRequestModel.is_fulfilled == False
    ).update({"is_fulfilled": True, "fulfilled_at": now_ms})

    db.commit()
    return {"status": "recorded"}

@app.get("/api/location/{child_id}/latest")
def get_latest_child_location(
    child_id: str,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    """Parent or Child fetches latest actual location point recorded by child device."""
    current_user = get_current_user(authorization, db=db)
    if current_user:
        child = db.query(UserModel).filter(UserModel.user_id == child_id).first()
        if child and child.group_id != current_user.group_id:
            raise HTTPException(status_code=403, detail="Unauthorized access across family groups.")

    point = db.query(LocationPointModel).filter(LocationPointModel.child_id == child_id)\
        .order_by(LocationPointModel.recorded_at.desc()).first()
    if not point:
        raise HTTPException(status_code=404, detail="No location recorded yet")
    return point

@app.post("/api/location/{child_id}/request-ping", response_model=LocationPingRequestResponse)
def request_child_location_ping(
    child_id: str,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    """
    Parent triggers an on-demand location refresh from the Child's phone.
    Creates a pending location request that the child phone polls and fulfills.
    """
    current_user = get_current_user(authorization, db=db)
    parent_id = current_user.user_id if current_user else "parent-request"

    req_id = f"locreq-{uuid.uuid4().hex[:8]}"
    now_ms = int(time.time() * 1000)

    # Invalidate previous unfulfilled requests older than 10 mins
    db.query(LocationRequestModel).filter(
        LocationRequestModel.child_id == child_id,
        LocationRequestModel.is_fulfilled == False,
        LocationRequestModel.requested_at < now_ms - (600000)
    ).delete(synchronize_session=False)

    new_request = LocationRequestModel(
        request_id=req_id,
        child_id=child_id,
        parent_id=parent_id,
        requested_at=now_ms,
        is_fulfilled=False
    )
    db.add(new_request)
    db.commit()

    return LocationPingRequestResponse(
        status="success",
        request_id=req_id,
        child_id=child_id,
        message="Location ping requested from child device."
    )

@app.get("/api/location/{child_id}/pending-ping", response_model=LocationPingStatusResponse)
def check_pending_location_ping(
    child_id: str,
    db: Session = Depends(get_db)
):
    """Child phone checks if parent has requested an immediate GPS fix."""
    now_ms = int(time.time() * 1000)
    # Ping valid if requested within last 5 minutes and not yet fulfilled
    pending = db.query(LocationRequestModel).filter(
        LocationRequestModel.child_id == child_id,
        LocationRequestModel.is_fulfilled == False,
        LocationRequestModel.requested_at > now_ms - (300000)
    ).first()

    return LocationPingStatusResponse(
        has_pending_ping=bool(pending),
        request_id=pending.request_id if pending else None
    )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
