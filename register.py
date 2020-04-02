import time
import uuid
import boto3

BUCKET_NAME = 'cfn-registry-six'
BUCKET_SUBDIR = 'subnet'
upload_id = str(uuid.uuid4())
UPLOAD_KEY = BUCKET_SUBDIR + '/handler-' + upload_id + '.jar'
FILE_NAME = './target/zugzwang-cloud-ipv6subnetconfiguration-handler-1.0-SNAPSHOT.jar'

zugzwang_session = boto3.session.Session(profile_name='zugzwang-dev', region_name='us-east-2')
s3_client = zugzwang_session.client('s3')
cfn_client = zugzwang_session.client('cloudformation')

print('Uploading handler jar with key ' + UPLOAD_KEY + '...')
s3_client.upload_file(FILE_NAME, BUCKET_NAME, UPLOAD_KEY, ExtraArgs={'ACL':'public-read'})
UPLOAD_URL = 's3://' + BUCKET_NAME + '/' + UPLOAD_KEY

print('Starting type registration...')
response = cfn_client.register_type(
    Type='RESOURCE',
    TypeName='Zugzwang::Cloud::IPv6SubnetConfiguration',
    SchemaHandlerPackage=UPLOAD_URL,
    LoggingConfig={
        'LogRoleArn': 'arn:aws:iam::394119796965:role/CloudFormationRegistryLogRole',
        'LogGroupName': 'Six'
    },
    ExecutionRoleArn='arn:aws:iam::394119796965:role/SixSubnetResources-ExecutionRole-R3QTP3MUJRYS'
)
registration_token = response['RegistrationToken']

print('Submitted registration and got back token ' + registration_token)

type_arn = None
type_version_arn = None

while True:
  response = cfn_client.describe_type_registration(
    RegistrationToken=registration_token
  )
  status = response['ProgressStatus']
  if status == 'IN_PROGRESS':
    print('Registration still in progress...')
    time.sleep(10)
  else:
    if status == 'COMPLETE':
      type_arn = response['TypeArn']
      type_version_arn = response['TypeVersionArn']
    break

if status == 'COMPLETE':
  print('Successfully registered type...')
  print('\tType ARN        : ' + type_arn)
  print('\tType Version ARN: ' + type_version_arn)
else:
  print('Registration failed.')