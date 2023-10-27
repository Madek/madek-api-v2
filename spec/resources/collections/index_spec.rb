require 'spec_helper'
require Pathname(File.expand_path('..', __FILE__)).join('shared')

describe 'a bunch of collections with different properties' do
  include_context :bunch_of_collections

  describe 'JSON `client` for authenticated `user`' do
    include_context :json_client_for_authenticated_user do
      describe 'the collections resource' do
        let :resource do
          #collections # force evaluation
          client.get('/api/collections')
        end

        it do
          expect(resource.status).to be == 200
        end
      end

    end
  end
end
